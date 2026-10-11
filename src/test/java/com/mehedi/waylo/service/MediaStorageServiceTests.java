package com.mehedi.waylo.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaStorageServiceTests {
    final Cloudinary cloud = mock(Cloudinary.class);
    final Uploader uploader = mock(Uploader.class);
    final MediaStorageService media = new MediaStorageService(cloud,Path.of("test-legacy-photos"));
    @BeforeEach void setup() { when(cloud.uploader()).thenReturn(uploader); }
    @AfterEach void cleanup() { if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization(); }
    byte[] png() throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();
    }
    @Test void rejectsDisguisedNonImageWithoutCallingCloudinary() {
        assertThrows(IllegalArgumentException.class,()->media.store(new MockMultipartFile("photo","fake.jpg","image/jpeg","<script>bad</script>".getBytes()),MediaStorageService.PhotoKind.POST));
        verifyNoInteractions(uploader);
    }
    @Test void rejectsOversizePhotoWithoutCallingCloudinary() {
        assertThrows(IllegalArgumentException.class,()->media.store(new MockMultipartFile("photo",new byte[6*1024*1024+1]),MediaStorageService.PhotoKind.POST));
        verifyNoInteractions(uploader);
    }
    @Test void reencodesPngAndUsesScopedPublicIdAndDynamicFolder() throws Exception {
        when(uploader.upload(any(),anyMap())).thenAnswer(invocation->{
            byte[] bytes=invocation.getArgument(0);Map<?,?> options=invocation.getArgument(1);
            assertEquals(0xff,Byte.toUnsignedInt(bytes[0]));assertEquals(0xd8,Byte.toUnsignedInt(bytes[1]));
            assertEquals("waylo/posts",options.get("asset_folder"));assertEquals(false,options.get("overwrite"));
            assertTrue(options.get("public_id").toString().startsWith("waylo/posts/"));
            return Map.of("secure_url","https://res.cloudinary.com/test/image/upload/photo.jpg","public_id",options.get("public_id"));
        });
        var result=media.store(new MockMultipartFile("photo","test.png","image/png",png()),MediaStorageService.PhotoKind.POST);
        assertNotNull(result);assertTrue(result.url().startsWith("https://"));
    }
    @Test void failedDatabaseTransactionCleansOnlyNewAsset() throws Exception {
        when(uploader.upload(any(),anyMap())).thenAnswer(i->Map.of("secure_url","https://res.cloudinary.com/test/photo.jpg","public_id",((Map<?,?>)i.getArgument(1)).get("public_id")));
        TransactionSynchronizationManager.initSynchronization();
        var result=media.store(new MockMultipartFile("photo","test.png","image/png",png()),MediaStorageService.PhotoKind.TRIP);
        for(var hook:TransactionSynchronizationManager.getSynchronizations())hook.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(uploader).destroy(eq(result.publicId()),anyMap());
    }
    @Test void replacementCleanupWaitsForCommitAndCannotDeleteOtherProject() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        String own="waylo/trips/00000000-0000-0000-0000-000000000001";
        media.deleteAfterCommit(own,null);verify(uploader,never()).destroy(anyString(),anyMap());
        for(var hook:TransactionSynchronizationManager.getSynchronizations())hook.afterCommit();
        verify(uploader).destroy(eq(own),anyMap());
        media.deleteAfterCommit("campusvoice/private-document",null);
        for(var hook:TransactionSynchronizationManager.getSynchronizations())hook.afterCommit();
        verify(uploader,never()).destroy(eq("campusvoice/private-document"),anyMap());
    }
    @Test void traversalCannotReadLegacyFiles() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->media.getPublicImage("../application.properties"));
    }
}
