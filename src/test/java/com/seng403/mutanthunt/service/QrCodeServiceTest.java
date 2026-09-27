package com.seng403.mutanthunt.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class QrCodeServiceTest {

    @Autowired QrCodeService qrCodeService;

    @Test
    void getJoinUrl_returnsUrl() {
        String url = qrCodeService.getJoinUrl();
        assertNotNull(url);
        assertTrue(url.startsWith("http"));
        assertTrue(url.endsWith("/"));
    }

    @Test
    void generateQrPng_returnsBytes() throws Exception {
        byte[] png = qrCodeService.generateQrPng(200);
        assertNotNull(png);
        assertTrue(png.length > 0);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);
        assertEquals((byte) 0x4E, png[2]);
        assertEquals((byte) 0x47, png[3]);
    }

    @Test
    void generateQrPng_differentSizes() throws Exception {
        byte[] small = qrCodeService.generateQrPng(100);
        byte[] large = qrCodeService.generateQrPng(500);
        assertTrue(large.length > small.length);
    }
}
