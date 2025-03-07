package com.tenacy.pixiescale.mediatranscoding.service;

import com.tenacy.pixiescale.mediatranscoding.domain.MediaFile;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

public interface MediaService {
    Mono<MediaFile> storeMedia(MultipartFile file);
    Mono<Resource> getMediaResource(String mediaId);
    Mono<MediaFile> getMediaInfo(String mediaId);
    Mono<Void> deleteMedia(String mediaId);
}