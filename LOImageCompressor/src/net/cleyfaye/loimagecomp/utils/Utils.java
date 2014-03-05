package net.cleyfaye.loimagecomp.utils;

import java.io.File;
import java.io.IOException;

/**
 * Utility code.
 * 
 * @author Cley Faye
 */
public class Utils {

    /**
     * Convert a file size (in bytes) to a human readable string.
     * 
     * @param fileSize
     *            The initial file size
     * @return The size, in a string.
     */
    public static String dataSizeToString(long fileSize)
    {
        final String[] suffixes = { "B", "kB", "MB", "GB", "TB" };
        int id = 0;
        while (id < suffixes.length - 1 && fileSize > 1500) {
            fileSize /= 1000;
            ++id;
        }
        return String.format("%d %s", fileSize, suffixes[id]);
    }

    /**
     * Return a file suffix.
     * 
     * @param str
     *            The file name
     * @return The file suffix (without the dot).
     */
    public static String getFileSuffix(final String str)
    {
        final int dotIndex = str.lastIndexOf('.');
        return dotIndex == -1 ? "" : str.substring(dotIndex + 1);
    }

    /**
     * Replace a file suffix.
     * 
     * @param str
     *            The file name
     * @param suffix
     *            The new file suffix
     * @return The renammed file
     */
    public static String replaceFileSuffix(final String str, final String suffix)
    {
        final int dotIndex = str.lastIndexOf('.');
        return dotIndex == -1 ? String.format("%s.%s", str, suffix) : String
                .format("%s.%s", str.substring(0, dotIndex), suffix);
    }

    /**
     * Get the image size from a string to a double.
     * 
     * @param sizeString
     *            The string containing the size. Either "XXcm" or "YYin" is
     *            accepted.
     * @return The size, in cm.
     */
    public static double sizeStringToDouble(final String sizeString)
            throws IOException
    {
        if (sizeString == null || sizeString.isEmpty()) {
            return 0;
        }
        if (sizeString.endsWith("cm")) {
            return Double.valueOf(sizeString.substring(0,
                    sizeString.length() - 2));
        } else if (sizeString.endsWith("in")) {
            // Not sure this is even possible. Too lazy to read full spec.
            return Double.valueOf(sizeString.substring(0,
                    sizeString.length() - 2)) * 2.54;
        }
        throw new IOException("Unexpected image size information");
    }

    /**
     * Create all the parent directory for the given file, and mark them for
     * deletion on vm shutdown.
     * 
     * @param tempFile
     *            The target file. All it's parent directories will be created.
     *            Any directory created this way will be marked for deletion on
     *            vm close. Pre-existing directories will NOT be marked.
     */
    public static void createTempFilePath(File tempFile)
    {
        File parent = tempFile.getParentFile();
        if (!parent.exists()) {
            createTempFilePath(parent);
            parent.mkdir();
            parent.deleteOnExit();
        }
    }
}
