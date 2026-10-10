package com.classing.shared.files
import kotlin.test.*
class AttachmentPolicyTest {
 @Test fun ordinaryFilesRemainSelectable() {
  for(name in listOf("report.pdf","notes.md","schedule.docx","data.xlsx","slides.pptx","book.epub","archive.zip","diagram.svg","picture.avif")) assertTrue(AttachmentPolicy.isAllowed(name,"application/octet-stream"), name)
 }
 @Test fun renamedExecutableAndMediaAreRejected() {
  for(signature in listOf("MZ","\u007fELF","#!","ID3","fLaC","OggS")) assertFalse(AttachmentPolicy.isAllowed("report.txt","text/plain",signature.toByteArray()))
  assertFalse(AttachmentPolicy.isAllowed("voice.pdf","audio/mpeg"));assertFalse(AttachmentPolicy.isAllowed("movie.mp4","application/octet-stream"));assertFalse(AttachmentPolicy.isAllowed("app.APK","application/octet-stream"))
 }
 @Test fun photoMagicDoesNotLookLikeMp3() {
  assertTrue(AttachmentPolicy.isAllowed("photo.jpg","image/jpeg",byteArrayOf(0xff.toByte(),0xd8.toByte(),0xff.toByte())))
  assertTrue(AttachmentPolicy.isAllowed("photo.webp","image/webp","RIFF0000WEBP".toByteArray()))
  assertFalse(AttachmentPolicy.isAllowed("renamed.txt","text/plain","RIFF0000WAVE".toByteArray()))
 }
}
