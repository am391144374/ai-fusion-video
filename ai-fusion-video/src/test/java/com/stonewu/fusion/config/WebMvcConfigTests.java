package com.stonewu.fusion.config;

import com.stonewu.fusion.service.storage.StorageConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebMvcConfigTests {

    @Test
    void usesTheBoundedMvcExecutorForReactiveAndSseRequests() {
        AsyncTaskExecutor executor = mock(AsyncTaskExecutor.class);
        WebMvcConfig config = new WebMvcConfig(
                mock(StorageConfigService.class), executor);
        AsyncSupportConfigurer asyncSupport = mock(AsyncSupportConfigurer.class);

        config.configureAsyncSupport(asyncSupport);

        verify(asyncSupport).setTaskExecutor(executor);
    }

    @Test
    void cachesLocalMediaForOneMonthBecauseUpdatedImagesUseNewUrls() {
        ResourceHandlerRegistration artStyleRegistration = mock(ResourceHandlerRegistration.class);
        ResourceHandlerRegistration mediaRegistration = mock(ResourceHandlerRegistration.class);
        ResourceHandlerRegistry registry = mock(ResourceHandlerRegistry.class);
        when(registry.addResourceHandler("/api/art-styles/**")).thenReturn(artStyleRegistration);
        when(registry.addResourceHandler("/media/**")).thenReturn(mediaRegistration);
        when(artStyleRegistration.addResourceLocations("classpath:/static/art-styles/"))
                .thenReturn(artStyleRegistration);
        when(mediaRegistration.addResourceLocations(org.mockito.ArgumentMatchers.any(String[].class)))
                .thenReturn(mediaRegistration);

        WebMvcConfig config = new WebMvcConfig(
                mock(StorageConfigService.class), mock(AsyncTaskExecutor.class));
        ReflectionTestUtils.setField(config, "localBasePath", "./data/media");

        config.addResourceHandlers(registry);

        verify(mediaRegistration).setCacheControl(argThat(cacheControl ->
                "max-age=2592000, public, immutable".equals(cacheControl.getHeaderValue())));
    }
}
