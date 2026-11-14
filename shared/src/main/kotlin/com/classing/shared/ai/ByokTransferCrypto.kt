package com.classing.shared.ai

import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.*
import javax.crypto.spec.*
import java.nio.ByteBuffer

object ByokTransferCrypto {
    fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    fun sessionKey(local: KeyPair, remotePublic: ByteArray, ownerId: String, nonce: ByteArray): ByteArray {
        require(remotePublic.size in 80..256 && nonce.size==32 && ownerId.isNotBlank())
        val public=KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(remotePublic))
        val secret=KeyAgreement.getInstance("ECDH").apply { init(local.private);doPhase(public,true) }.generateSecret()
        return MessageDigest.getInstance("SHA-256").digest("Classing-BYOK-v1:$ownerId".toByteArray()+nonce+secret).also { secret.fill(0) }
    }
    fun code(key: ByteArray): String = ((ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(key)).int.toLong() and 0xffffffffL)%1_000_000).toString().padStart(6,'0')
    fun encrypt(key: ByteArray, data: ByteArray, purpose: String): ByteArray {
        require(data.size<=128*1024)
        val iv=ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv));cipher.updateAAD(purpose.toByteArray())
        return iv+cipher.doFinal(data)
    }
    fun decrypt(key: ByteArray, packet: ByteArray, purpose: String): ByteArray {
        require(packet.size in 28..(128*1024+28))
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,packet.copyOfRange(0,12)));cipher.updateAAD(purpose.toByteArray())
        return cipher.doFinal(packet.copyOfRange(12,packet.size))
    }
}
