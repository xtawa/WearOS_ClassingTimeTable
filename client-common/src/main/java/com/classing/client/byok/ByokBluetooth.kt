package com.classing.client.byok

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.classing.shared.ai.*
import java.io.*
import java.security.SecureRandom
import java.util.*
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

data class ByokPairedDevice(val name: String, val address: String)

/** Secure RFCOMM only. This file never references Wear DataClient/MessageClient or an Internet endpoint. */
object ByokBluetooth {
    private val serviceId=UUID.fromString("7d26bf85-2e0c-43a8-9c19-f138ab0d9d91")
    fun permitted(context: Context): Boolean = Build.VERSION.SDK_INT<31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED
    private fun adapter(context: Context): BluetoothAdapter {
        check(permitted(context)) { "Allow Nearby devices permission" }
        return (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.also {
            check(it.isEnabled) { "Turn Bluetooth on" }
        } ?: error("Bluetooth is unavailable")
    }
    @SuppressLint("MissingPermission")
    fun devices(context: Context): List<ByokPairedDevice> = adapter(context).bondedDevices
        .map { ByokPairedDevice(it.name ?: "Wear device",it.address) }.sortedBy { it.name }

    private fun write(out: DataOutputStream, bytes: ByteArray) { require(bytes.size<=128*1024+28);out.writeInt(bytes.size);out.write(bytes);out.flush() }
    private fun read(input: DataInputStream): ByteArray { val length=input.readInt();require(length in 1..(128*1024+28));return ByteArray(length).also(input::readFully) }

    @SuppressLint("MissingPermission")
    suspend fun send(context: Context, device: ByokPairedDevice, config: ByokConfig, confirm: suspend (String) -> Boolean) = withTimeout(120_000) {
        val bt=adapter(context)
        val receiver=bt.bondedDevices.firstOrNull { it.address==device.address } ?: error("Device must already be paired")
        val socket=receiver.createRfcommSocketToServiceRecord(serviceId)
        // Register cancellation before connect, which is a blocking system call.
        cancellableIo { close ->
            close { socket.close() };socket.connect()
            val input=DataInputStream(socket.inputStream);val output=DataOutputStream(socket.outputStream)
            val hello=JSONObject(String(read(input),Charsets.UTF_8));require(hello.getInt("version")==1 && hello.getString("ownerId")==config.ownerId && hello.getString("appId")==context.packageName) { "Use the same Classing edition and account on both devices" }
            val nonce=Base64.getDecoder().decode(hello.getString("nonce"));val pair=ByokTransferCrypto.keyPair()
            val key=ByokTransferCrypto.sessionKey(pair,Base64.getDecoder().decode(hello.getString("publicKey")),config.ownerId+":"+context.packageName,nonce)
            try {
                write(output,pair.public.encoded)
                check(runBlocking { confirm(ByokTransferCrypto.code(key)) }) { "Transfer cancelled" }
                write(output,ByokTransferCrypto.encrypt(key,ByokJson.encode(config.forWear()).toString().toByteArray(),"config"))
                val ack=String(ByokTransferCrypto.decrypt(key,read(input),"ack"),Charsets.UTF_8)
                check(ack=="SAVED") { "Wear did not confirm saving" }
            } finally { key.fill(0) }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun receive(context: Context, ownerId: String, onCode: (String) -> Unit) = withTimeout(120_000) {
        val bt=adapter(context)
        cancellableIo { close ->
            val server=bt.listenUsingRfcommWithServiceRecord("Classing private BYOK",serviceId);close { server.close() }
            val socket=server.accept(120_000);close { socket.close() };server.close()
            require(socket.remoteDevice.bondState==BluetoothDevice.BOND_BONDED) { "Device must already be paired" }
            val input=DataInputStream(socket.inputStream);val output=DataOutputStream(socket.outputStream)
            val pair=ByokTransferCrypto.keyPair();val nonce=ByteArray(32).also { SecureRandom().nextBytes(it) }
            write(output,JSONObject().put("version",1).put("ownerId",ownerId).put("appId",context.packageName).put("nonce",Base64.getEncoder().encodeToString(nonce))
                .put("publicKey",Base64.getEncoder().encodeToString(pair.public.encoded)).toString().toByteArray())
            val key=ByokTransferCrypto.sessionKey(pair,read(input),ownerId+":"+context.packageName,nonce)
            try {
                onCode(ByokTransferCrypto.code(key))
                val decoded=ByokTransferCrypto.decrypt(key,read(input),"config")
                val config=try { ByokJson.decode(JSONObject(String(decoded,Charsets.UTF_8))) } finally { decoded.fill(0) }
                require(config.ownerId==ownerId && config.profiles.size==1 && config.enabled)
                ByokStore.save(context,config)
                write(output,ByokTransferCrypto.encrypt(key,"SAVED".toByteArray(),"ack"))
            } finally { key.fill(0) }
        }
    }
}
