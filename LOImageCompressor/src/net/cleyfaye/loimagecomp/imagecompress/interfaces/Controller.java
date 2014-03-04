package net.cleyfaye.loimagecomp.imagecompress.interfaces;

import java.io.File;

import net.cleyfaye.loimagecomp.utils.ProgressCheck;

/**
 * Controller code to resample images in an ODT.
 * 
 * @author Cley Faye
 */
public interface Controller {

    /** Target sampling quality */
    public static enum SampleQuality {
        /** Fast resampling; less pretty. */
        SQ_FAST,
        /** Smooth (bicubic) resampling; better, slower. */
        SQ_SMOOTH
    }

    /** Is a file open ? */
    public boolean isFileOpen();

    /** Open a new odt file */
    public void openFile(File odtFile, ProgressCheck progressCheck)
            throws Exception;

    /**
     * Save the file
     * 
     * @return false if the operation was interrupted.
     */
    public boolean saveFile(File odtFile, ProgressCheck progressCheck)
            throws Exception;
}
