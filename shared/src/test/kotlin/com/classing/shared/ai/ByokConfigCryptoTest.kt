package com.classing.shared.ai

import kotlin.test.*

class ByokConfigCryptoTest {
    private fun profile(id: String, role: ByokRole=ByokRole.CHAT) = ByokProfile(id,"Test","https://provider.example/v1","test-model","test-only-private-key",role)
    @Test fun transfersOnlyChosenWearModel() {
        val c=ByokConfig("owner",listOf(profile("chat"),profile("vision",ByokRole.VISION),profile("voice",ByokRole.TRANSCRIPTION)),true,"chat","vision","voice",wearId="vision")
        val wear=c.forWear()
        assertEquals(listOf("vision"),wear.profiles.map {it.id});assertEquals(ByokRole.CHAT,wear.profiles.single().role)
        assertEquals("vision",wear.chatId);assertTrue(wear.enabled);assertEquals("",wear.transcriptionId)
    }
    @Test fun deletingASelectedProfileDisablesItAndClearsSelectors() {
        val c=ByokConfig("owner",listOf(profile("chat")),true,chatId="chat",wearId="chat").remove("chat")
        assertFalse(c.enabled);assertEquals("",c.chatId);assertEquals("",c.wearId)
    }
    @Test fun rejectsInvalidEndpointsAndRoleAssignmentsWithoutPrintingKeys() {
        for(url in listOf("http://provider.example/v1","https://key@provider.example/v1","https://provider.example/v1?key=secret","https://provider.example/v1#secret"))
            assertFailsWith<IllegalArgumentException> {profile("a").copy(baseUrl=url)}
        assertFailsWith<IllegalArgumentException> {ByokConfig("owner",listOf(profile("voice",ByokRole.SPEECH)),true,chatId="voice")}
        assertFailsWith<IllegalArgumentException> {profile("a").copy(apiKey="secret\r\nInjected: value")}
        assertFailsWith<IllegalArgumentException> {profile("a").copy(apiKey="test-key-with-\u00a0-unicode-space")}
        assertFalse(profile("a").toString().contains("test-only-private-key"))
    }
    @Test fun ecdhMatchesAndBindsOwnerAndNonce() {
        val phone=ByokTransferCrypto.keyPair();val watch=ByokTransferCrypto.keyPair();val nonce=ByteArray(32) {it.toByte()}
        val p=ByokTransferCrypto.sessionKey(phone,watch.public.encoded,"owner",nonce)
        val w=ByokTransferCrypto.sessionKey(watch,phone.public.encoded,"owner",nonce)
        assertContentEquals(p,w);assertEquals(ByokTransferCrypto.code(p),ByokTransferCrypto.code(w));assertEquals(6,ByokTransferCrypto.code(p).length)
        assertFalse(p.contentEquals(ByokTransferCrypto.sessionKey(watch,phone.public.encoded,"different-owner",nonce)))
        assertFalse(p.contentEquals(ByokTransferCrypto.sessionKey(watch,phone.public.encoded,"owner",ByteArray(32))))
    }
    @Test fun authenticatedPacketsRejectTamperAndWrongPurposeOrKey() {
        val key=ByteArray(32) {it.toByte()};val text="local confidential configuration".toByteArray()
        val packet=ByokTransferCrypto.encrypt(key,text,"config")
        assertContentEquals(text,ByokTransferCrypto.decrypt(key,packet,"config"))
        assertFalse(packet.contentEquals(ByokTransferCrypto.encrypt(key,text,"config")))
        val tampered=packet.copyOf().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()}
        assertFails {ByokTransferCrypto.decrypt(key,tampered,"config")}
        assertFails {ByokTransferCrypto.decrypt(key,packet,"ack")}
        assertFails {ByokTransferCrypto.decrypt(ByteArray(32),packet,"config")}
        assertFailsWith<IllegalArgumentException> {ByokTransferCrypto.decrypt(key,ByteArray(27),"config")}
    }
}
