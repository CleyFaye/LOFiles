package net.cleyfaye.loimagecomp.imagecompress;

import static net.cleyfaye.loimagecomp.utils.Utils.getFileSuffix;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

import net.cleyfaye.loimagecomp.imagecompress.interfaces.ImageFilter;

/**
 * Dummy filter. Pass images through.
 * 
 * @author Cley Faye
 */
public class DummyImageFilter implements ImageFilter {

    @Override
    public boolean filterImage(final File tempDir, final ImageInfo imageInfo,
            final OutputStream output) throws FileNotFoundException,
            IOException
    {
        try (FileInputStream fis = new FileInputStream(new File(tempDir,
                imageInfo.getRelativeName()))) {
            final byte[] buffer = new byte[4096];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }
        }
        return true;
    }

    @Override
    public String filterImageSuffix(final File tempDir,
            final ImageInfo imageInfo) throws Exception
    {
        return getFileSuffix(imageInfo.getRelativeName());
    }

}
