package net.cleyfaye.loimagecomp.imagecompress;

import static net.cleyfaye.loimagecomp.utils.Utils.getFileSuffix;

import java.io.FileInputStream;
import java.io.OutputStream;

import net.cleyfaye.loimagecomp.imagecompress.interfaces.ImageFilter;

/**
 * Dummy filter. Pass images through.
 * 
 * @author Cley Faye
 */
public class DummyImageFilter implements ImageFilter {

    @Override
    public void getImageData(final ImageInfo imageInfo,
            final OutputStream output) throws Exception
    {
        try (FileInputStream fis = new FileInputStream(imageInfo.getFile())) {
            final byte[] buffer = new byte[4096];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        }
    }

    @Override
    public String getImageSuffix(final ImageInfo imageInfo) throws Exception
    {
        return getFileSuffix(imageInfo.getRelativeName());
    }

    @Override
    public void prepareImage(final ImageInfo imageInfo) throws Exception
    {
        // Nothing to do here
    }

}
