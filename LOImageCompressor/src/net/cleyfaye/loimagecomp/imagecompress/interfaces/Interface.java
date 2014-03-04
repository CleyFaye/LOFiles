package net.cleyfaye.loimagecomp.imagecompress.interfaces;

import java.util.List;

import net.cleyfaye.loimagecomp.imagecompress.ImageInfo;
import net.cleyfaye.loimagecomp.imagecompress.interfaces.Controller.SampleQuality;

/**
 * Interface to get/display info.
 * 
 * @author Cley Faye
 */
public interface Interface {

    /** Return the selected jpeg quality */
    public int getJPEGQuality();

    /** Should we kill the transparency ? */
    public boolean getKillTransparency();

    /** Return the selected sample quality */
    public SampleQuality getSampleQuality();

    /** Return the selected target dpi */
    public double getTargetDPI();

    /** Display all the detected images */
    public void updateImagesList(List<ImageInfo> images);
}
