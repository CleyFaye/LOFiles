package net.cleyfaye.loimagecomp.imagecompress.interfaces;

import java.io.File;
import java.io.OutputStream;

import net.cleyfaye.loimagecomp.imagecompress.ImageInfo;

/** A filter that process each image from source to destination. */
public interface ImageFilter {
    public boolean filterImage(File tempDir, ImageInfo imageInfo,
            OutputStream output) throws Exception;

    public String filterImageSuffix(File tempDir, ImageInfo imageInfo)
            throws Exception;
}
