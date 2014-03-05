package net.cleyfaye.loimagecomp.imagecompress;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import net.cleyfaye.loimagecomp.imagecompress.interfaces.Controller;
import net.cleyfaye.loimagecomp.imagecompress.interfaces.ImageFilter;
import net.cleyfaye.loimagecomp.imagecompress.interfaces.Interface;
import net.cleyfaye.loimagecomp.utils.ProgressCheck;
import net.cleyfaye.loimagecomp.utils.ProgressCheck.Instance;

import org.xml.sax.SAXException;

/**
 * All the "real work" about filtering an ODT pictures is here.
 * 
 * @author Cley Faye
 */
public class ImageCompress implements Controller, Interface {

    private final Interface mInterface;
    private ODTFile mODTFile = null;

    public ImageCompress(final Interface intf) {
        mInterface = intf;
    }

    @Override
    public int getJPEGQuality()
    {
        if (mInterface != null) {
            return mInterface.getJPEGQuality();
        }
        return 80;
    }

    @Override
    public boolean getKillTransparency()
    {
        if (mInterface != null) {
            return mInterface.getKillTransparency();
        }
        return false;
    }

    @Override
    public SampleQuality getSampleQuality()
    {
        if (mInterface != null) {
            return mInterface.getSampleQuality();
        }
        return SampleQuality.SQ_SMOOTH;
    }

    @Override
    public boolean isFileOpen()
    {
        return mODTFile != null;
    }

    @Override
    public void openFile(final File odtFile, final ProgressCheck progressCheck)
            throws IOException, ParserConfigurationException, SAXException
    {
        final Instance progress = new Instance(progressCheck);
        progress.startProgress("Loading file");
        mODTFile = new ODTFile(odtFile, progress);
        updateImagesList(mODTFile.mImages);
        progress.endProgress();
    }

    @Override
    public boolean saveFile(final File odtFile,
            final ProgressCheck progressCheck) throws IOException, Exception
    {
        final Instance progress = new Instance(progressCheck);
        progress.startProgress("Saving file");
        progress.progressNewMaxValue(mODTFile.getImagesCount() * 2);
        final int jpegQuality = getJPEGQuality();
        final int sampleQuality = getSampleQuality() == SampleQuality.SQ_FAST ? 1
                : 0;
        final boolean killTransparency = getKillTransparency();
        final ImageFilter imageFilter = new DownsampleImageFilter(jpegQuality,
                sampleQuality, killTransparency);
        final boolean result = mODTFile.createCopy(odtFile, imageFilter,
                progress);
        progress.endProgress();
        return result;
    }

    @Override
    public void updateImagesList(final List<ImageInfo> images)
    {
        if (mInterface != null) {
            mInterface.updateImagesList(images);
        }
    }

}
