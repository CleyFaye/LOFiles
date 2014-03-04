package net.cleyfaye.loimagecomp;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.Action;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.ProgressMonitor;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;

import net.cleyfaye.loimagecomp.ODTFile.ImageFilter;

/**
 * Application main window
 * 
 * TODO Implement a controller somewhere to separate GUI and useful code
 * 
 * TODO Implement a CLI at some point
 * 
 * TODO Implement a margin at which the image are not actually resized (for
 * example to not resize things from 500x500 to 498x498)
 * 
 * @author Cley Faye
 */
public class MainWindow {

    /**
     * Filter to resize images when saving ODT file.
     * 
     * TODO move this out of the GUI class!
     * 
     * @author Cley Faye
     */
    private static class ImageFilterer implements ImageFilter {

        /** Target DPI */
        private final double mDPI;
        /** Target JPG quality */
        private final int mJPGQuality;
        /** Target Interpolation mode */
        private Object mInterpolation;
        /** Do we retain transparency or not */
        private final boolean mKillTransparency;
        /** Temporary directory for compressed images */
        private final File mTempDir = Files.createTempDirectory("loimgcomp")
                .toFile();
        /** List of temporary files for each images path */
        private final Map<String, File> mImageFiles = new HashMap<>();
        /** Progress dialog */
        private final ProgressMonitor mMonitor;
        /** Number of images processed. This actually go up to 2*imagecount */
        private int mProcessedImages = 0;

        public ImageFilterer(final double dpi, final int jpgQuality,
                final int scalingMethod, final boolean killTransparency,
                final ProgressMonitor monitor) throws IOException {
            mMonitor = monitor;
            mDPI = dpi;
            mJPGQuality = jpgQuality;
            mKillTransparency = killTransparency;
            mTempDir.deleteOnExit();
            switch (scalingMethod) {
            default:
            case 0:
                mInterpolation = RenderingHints.VALUE_INTERPOLATION_BICUBIC;
                break;
            case 1:
                mInterpolation = RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
                break;
            }
        }

        @Override
        public boolean filterImage(final File tempDir,
                final ImageInfo imageInfo, final OutputStream output)
                throws Exception
        {
            // At this point, each pictures is already saved somewhere.
            if (mMonitor.isCanceled()) {
                return false;
            }
            final byte[] buffer = new byte[4096];
            final File file = mImageFiles.get(imageInfo.getRelativeName());
            try (FileInputStream fis = new FileInputStream(file)) {
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    output.write(buffer, 0, length);
                }
            }
            file.delete();
            mMonitor.setProgress(++mProcessedImages);
            return true;
        }

        @Override
        public String filterImageSuffix(final File tempDir,
                final ImageInfo imageInfo) throws Exception
        {
            // This function is called one time on each image, before actually
            // saving. Since we need to know the final image suffix at this
            // point, we have to save in each format to see which one is most
            // efficient.
            // Temp save file is stored for the next step.
            if (mMonitor.isCanceled()) {
                return null;
            }
            final ImageSize targetImageSize = projectImageSize(
                    imageInfo.getImageSizePx(), imageInfo.getDrawSizeCm(), mDPI);
            final BufferedImage original = ImageIO.read(new File(tempDir,
                    imageInfo.getRelativeName()));
            final int imageType = mKillTransparency ? BufferedImage.TYPE_INT_RGB
                    : original.getType();
            final BufferedImage resized = new BufferedImage(
                    (int) targetImageSize.getX(), (int) targetImageSize.getY(),
                    imageType);
            final Graphics2D gc = (Graphics2D) resized.getGraphics();
            gc.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            gc.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    mInterpolation);
            gc.drawImage(original, 0, 0, (int) targetImageSize.getX(),
                    (int) targetImageSize.getY(), null);
            gc.dispose();

            if (original.getTransparency() == Transparency.OPAQUE
                    || mKillTransparency) {
                // Either opaque, or kill transparency; save as jpeg or png,
                // whichever is better
                final ImageWriter writer = ImageIO.getImageWritersBySuffix(
                        "jpg").next();
                final ImageWriteParam iwp = writer.getDefaultWriteParam();
                iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                iwp.setCompressionQuality(mJPGQuality / 100f);
                final File tempJPG = Files.createTempFile("loimgcomp", "jpg")
                        .toFile();
                tempJPG.deleteOnExit();
                final File tempPNG = Files.createTempFile("loimgcomp", "png")
                        .toFile();
                tempPNG.deleteOnExit();
                try (ImageOutputStream ios = ImageIO
                        .createImageOutputStream(new FileOutputStream(tempJPG))) {
                    writer.setOutput(ios);
                    writer.write(null, new IIOImage(resized, null, null), iwp);
                }
                try (ImageOutputStream ios = ImageIO
                        .createImageOutputStream(new FileOutputStream(tempPNG))) {
                    writer.setOutput(ios);
                    writer.write(resized);
                }
                if (tempPNG.length() < tempJPG.length()) {
                    // Use png
                    tempJPG.delete();
                    mImageFiles.put(imageInfo.getRelativeName(), tempPNG);
                    mMonitor.setProgress(++mProcessedImages);
                    return "png";
                } else {
                    // Use jpg
                    tempPNG.delete();
                    mImageFiles.put(imageInfo.getRelativeName(), tempJPG);
                    mMonitor.setProgress(++mProcessedImages);
                    return "jpg";
                }
            } else {
                // Save as png
                final File tempPNG = Files.createTempFile("loimgcomp", "png")
                        .toFile();
                tempPNG.deleteOnExit();
                final ImageWriter writer = ImageIO.getImageWritersBySuffix(
                        "png").next();
                try (ImageOutputStream ios = ImageIO
                        .createImageOutputStream(new FileOutputStream(tempPNG))) {
                    writer.setOutput(ios);
                    writer.write(resized);
                }
                mImageFiles.put(imageInfo.getRelativeName(), tempPNG);
                mMonitor.setProgress(++mProcessedImages);
                return "png";
            }
        }

    }

    private class OpenAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        public OpenAction() {
            putValue(NAME, "Open...");
            putValue(SHORT_DESCRIPTION, "Some short description");
        }

        @Override
        public void actionPerformed(final ActionEvent e)
        {
            try {
                final JFileChooser fc = new JFileChooser();
                final FileFilter filter = new FileFilter() {

                    @Override
                    public boolean accept(final File f)
                    {
                        return f.getName().endsWith(".odt") || f.isDirectory();
                    }

                    @Override
                    public String getDescription()
                    {
                        return "OpenDocument Text (*.odt)";
                    }
                };
                fc.addChoosableFileFilter(filter);
                fc.setFileFilter(filter);
                final int returnVal = fc.showOpenDialog(mframe);
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    mDocODT = new ODTFile(fc.getSelectedFile());
                    refreshImagesList();
                }
            } catch (final Exception e2) {
                e2.printStackTrace();
            }
        }
    }

    private class SaveAction extends AbstractAction {
        private static final long serialVersionUID = 1L;

        public SaveAction() {
            putValue(NAME, "Save...");
            putValue(SHORT_DESCRIPTION, "Some short description");
        }

        @Override
        public void actionPerformed(final ActionEvent e)
        {
            if (mDocODT == null) {
                return;
            }
            try {
                final JFileChooser fc = new JFileChooser();
                final FileFilter filter = new FileFilter() {

                    @Override
                    public boolean accept(final File f)
                    {
                        return f.getName().endsWith(".odt") || f.isDirectory();
                    }

                    @Override
                    public String getDescription()
                    {
                        return "OpenDocument Text (*.odt)";
                    }
                };
                fc.addChoosableFileFilter(filter);
                fc.setFileFilter(filter);
                final int returnVal = fc.showSaveDialog(mframe);
                if (returnVal != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                final ProgressMonitor monitor = new ProgressMonitor(mframe,
                        "Compressing images", "", 0,
                        mDocODT.getImagesCount() * 2);
                mframe.setEnabled(false);
                // TODO Maybe swingworker isn't the best choice here
                final SwingWorker<Integer, Integer> sw = new SwingWorker<Integer, Integer>() {

                    private boolean mResult;

                    @Override
                    protected Integer doInBackground() throws Exception
                    {
                        mResult = mDocODT.createCopy(
                                fc.getSelectedFile(),
                                new ImageFilterer(mDPI,
                                        ((Integer) mJpgQualitySpinner
                                                .getValue()).intValue(),
                                        mScalingMethodCombo.getSelectedIndex(),
                                        mKillTransparencyCheck.isSelected(),
                                        monitor));
                        return null;
                    }

                    @Override
                    protected void done()
                    {
                        if (mResult) {
                            final long previousSize = mDocODT.getSize();
                            final long newSize = fc.getSelectedFile().length();
                            JOptionPane.showMessageDialog(mframe,
                                    "Save complete. Initial file size: "
                                            + fileSizeToString(previousSize)
                                            + ", new size:"
                                            + fileSizeToString(newSize));
                        } else {
                            JOptionPane.showMessageDialog(mframe,
                                    "Operation aborted");
                        }
                        mframe.setEnabled(true);
                    }

                };
                sw.execute();
            } catch (final Exception e2) {
                e2.printStackTrace();
            }
        }
    }

    /**
     * Return a file suffix.
     * 
     * TODO avoid issue when file don't have a suffix TODO move this in utility
     * 
     * @param str
     *            The file name
     * @return The file suffix (without the dot).
     */
    public static String getFileSuffix(final String str)
    {
        return str.substring(str.lastIndexOf('.') + 1);
    }

    /**
     * Launch the application.
     */
    public static void main(final String[] args)
    {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (final Throwable e) {
            e.printStackTrace();
        }
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run()
            {
                try {
                    final MainWindow window = new MainWindow();
                    window.mframe.setVisible(true);
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Compute new image resolution after resizing.
     * 
     * This can shrink images, but never make them bigger. The new size will
     * never be less than 1x1. If the draw size is less than 0.1x0.1, no
     * resizing occurs (it's used to indicate that we don't know the intended
     * print size).
     * 
     * TODO move this out of the GUI class!
     * 
     * @param originalSizePx
     *            Original image size in pixels
     * @param drawSizeCm
     *            Intended print size in cm. If 0x0, it's supposed to be
     *            unknown.
     * @param dpi
     *            Target DPI
     * @return The new image size, in pixel.
     */
    static private ImageSize projectImageSize(final ImageSize originalSizePx,
            final ImageSize drawSizeCm, final double dpi)
    {
        if (drawSizeCm.getX() < 0.1 || drawSizeCm.getY() < 0.1) {
            return originalSizePx;
        }
        final double dpcm = dpi * 0.393701;
        final ImageSize targetSize = new ImageSize(drawSizeCm.getX() * dpcm,
                drawSizeCm.getY() * dpcm);
        if (targetSize.getX() > originalSizePx.getX()) {
            targetSize.setX(originalSizePx.getX());
        }
        if (targetSize.getY() > originalSizePx.getY()) {
            targetSize.setY(originalSizePx.getY());
        }
        targetSize.setX(Math.round(targetSize.getX()));
        targetSize.setY(Math.round(targetSize.getY()));
        if (targetSize.getX() < 1) {
            targetSize.setX(1);
        }
        if (targetSize.getY() < 1) {
            targetSize.setY(1);
        }
        return targetSize;
    }

    /**
     * Replace a file suffix.
     * 
     * TODO avoid issue when file don't have a suffix TODO move this in utility
     * 
     * @param str
     *            The file name
     * @param suffix
     *            The new file suffix
     * @return The renammed file
     */
    public static String replaceSuffix(final String str, final String suffix)
    {
        return str.substring(0, str.lastIndexOf('.') + 1) + suffix;
    }

    // GUI stuff
    private JFrame mframe;
    private final Action actionOpen = new OpenAction();
    private final Action actionSave = new SaveAction();
    private JList<String> mDetectedImagesList;
    private JLabel mOriginalResLabel;
    private JSpinner mJpgQualitySpinner;

    private JComboBox<String> mScalingMethodCombo;
    private JLabel mImageSizeLabel;

    private JPanel mImageDetailsGroup;

    private JLabel mTargetResLabel;

    private JLabel mOriginalSizeLabel;

    private JSpinner mTargetDPISpinner;

    private JCheckBox mKillTransparencyCheck;

    /** Currently open ODT file */
    private ODTFile mDocODT;

    /**
     * Output DPI
     * 
     * TODO just implement a getter/setter for this that peek/poke the GUI
     */
    private double mDPI = 90;

    /**
     * Create the application.
     */
    public MainWindow() {
        initialize();
    }

    /**
     * Convert a file size (in bytes) to a human readable string.
     * 
     * TODO move this into utility class
     * 
     * @param fileSize
     *            The initial file size
     * @return The size, in a string.
     */
    private String fileSizeToString(long fileSize)
    {
        final String[] suffixes = { "B", "kB", "MB", "GB", "TB" };
        int id = 0;
        while (id < suffixes.length - 1 && fileSize > 1500) {
            fileSize /= 1000;
            ++id;
        }
        return String.valueOf(fileSize) + " " + suffixes[id];
    }

    /**
     * Initialize the contents of the frame.
     */
    private void initialize()
    {
        mframe = new JFrame();
        mframe.setBounds(100, 100, 847, 766);
        mframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        final JMenuBar menuBar = new JMenuBar();
        mframe.setJMenuBar(menuBar);

        final JMenu menuFile = new JMenu("File");
        menuBar.add(menuFile);

        menuFile.add(actionOpen);

        final JMenuItem menuFileItemSave = menuFile.add(actionSave);
        menuFileItemSave.setText("Save...");

        final JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(null, "Detected images",
                TitledBorder.LEADING, TitledBorder.TOP, null, null));
        mframe.getContentPane().add(panel, BorderLayout.CENTER);
        panel.setLayout(new BorderLayout(0, 0));

        mDetectedImagesList = new JList<String>();
        mDetectedImagesList
                .addListSelectionListener(new ListSelectionListener() {
                    @Override
                    public void valueChanged(final ListSelectionEvent arg0)
                    {
                        final int index = arg0.getFirstIndex();
                        if (index >= 0) {
                            showImageDetails();
                        }
                    }
                });
        mDetectedImagesList.setModel(new AbstractListModel<String>() {
            private static final long serialVersionUID = 1L;
            String[] values = new String[] {};

            @Override
            public String getElementAt(final int index)
            {
                return values[index];
            }

            @Override
            public int getSize()
            {
                return values.length;
            }
        });
        mDetectedImagesList
                .setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        final JScrollPane scrollPane = new JScrollPane(mDetectedImagesList);
        panel.add(scrollPane, BorderLayout.CENTER);

        mImageDetailsGroup = new JPanel();
        mImageDetailsGroup.setVisible(false);
        mImageDetailsGroup.setBorder(new TitledBorder(null, "Image details",
                TitledBorder.LEADING, TitledBorder.TOP, null, null));
        mframe.getContentPane().add(mImageDetailsGroup, BorderLayout.EAST);
        mImageDetailsGroup.setLayout(new GridLayout(0, 2, 0, 0));

        final JLabel lblNewLabel = new JLabel("Original resolution:");
        mImageDetailsGroup.add(lblNewLabel);

        mOriginalResLabel = new JLabel("<>");
        mImageDetailsGroup.add(mOriginalResLabel);

        final JLabel lblImageSize = new JLabel("Max image size:");
        mImageDetailsGroup.add(lblImageSize);

        mImageSizeLabel = new JLabel("<>");
        mImageDetailsGroup.add(mImageSizeLabel);

        final JLabel lblNewLabel_2 = new JLabel("Projected resolution:");
        mImageDetailsGroup.add(lblNewLabel_2);

        mTargetResLabel = new JLabel("<>");
        mImageDetailsGroup.add(mTargetResLabel);

        final JLabel lblNewLabel_1 = new JLabel("Original file size:");
        mImageDetailsGroup.add(lblNewLabel_1);

        mOriginalSizeLabel = new JLabel("<>");
        mImageDetailsGroup.add(mOriginalSizeLabel);

        final JPanel panel_2 = new JPanel();
        panel_2.setBorder(new TitledBorder(null, "Output settings",
                TitledBorder.LEADING, TitledBorder.TOP, null, null));
        mframe.getContentPane().add(panel_2, BorderLayout.NORTH);

        final JPanel panel_4 = new JPanel();
        panel_2.add(panel_4);

        final JLabel lblTargetDpi = new JLabel("Target DPI:");
        panel_4.add(lblTargetDpi);

        mTargetDPISpinner = new JSpinner();
        mTargetDPISpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(final ChangeEvent arg0)
            {
                mDPI = ((Float) mTargetDPISpinner.getValue()).doubleValue();
                showImageDetails();
            }
        });
        mTargetDPISpinner.setPreferredSize(new Dimension(70, 20));
        mTargetDPISpinner.setMinimumSize(new Dimension(70, 20));
        mTargetDPISpinner.setModel(new SpinnerNumberModel(new Float(90), null,
                null, new Float(1)));
        panel_4.add(mTargetDPISpinner);

        final JPanel mJpgQualityGroup = new JPanel();
        panel_2.add(mJpgQualityGroup);

        final JLabel lblNewLabel_3 = new JLabel("Jpeg quality:");
        mJpgQualityGroup.add(lblNewLabel_3);

        mJpgQualitySpinner = new JSpinner();
        mJpgQualitySpinner.setModel(new SpinnerNumberModel(75, 0, 100, 5));
        mJpgQualityGroup.add(mJpgQualitySpinner);

        mKillTransparencyCheck = new JCheckBox(
                "Kill transparency (save all as jpeg)");
        panel_2.add(mKillTransparencyCheck);

        final JPanel panel_3 = new JPanel();
        panel_2.add(panel_3);

        final JLabel lblScalingMethod = new JLabel("Scaling method:");
        panel_3.add(lblScalingMethod);

        mScalingMethodCombo = new JComboBox<>();
        mScalingMethodCombo.setModel(new DefaultComboBoxModel<String>(
                new String[] { "Fast", "Smooth" }));
        mScalingMethodCombo.setSelectedIndex(1);
        panel_3.add(mScalingMethodCombo);
    }

    private void refreshImagesList()
    {
        mImageDetailsGroup.setVisible(false);
        final DefaultListModel<String> model = new DefaultListModel<>();
        for (int imgCount = 0; imgCount < mDocODT.getImagesCount(); ++imgCount) {
            final ImageInfo info = mDocODT.getImageInfo(imgCount);
            if (!info.isEmbedded()) {
                model.addElement("<not embedded>");
            } else {
                model.addElement(info.getRelativeName());
            }
        }
        mDetectedImagesList.setModel(model);
        mDetectedImagesList.setSelectedIndex(-1);
    }

    /** Display current image details and projected size in the GUI */
    private void showImageDetails()
    {
        final int index = mDetectedImagesList.getSelectedIndex();
        if (index == -1) {
            mImageDetailsGroup.setVisible(false);
            return;
        }
        final ImageInfo info = mDocODT.getImageInfo(index);
        if (!info.isEmbedded()) {
            mImageDetailsGroup.setVisible(false);
            return;
        }
        mOriginalResLabel.setText(info.getImageSizePx() + " px");
        mImageSizeLabel.setText(info.getDrawSizeCm() + " cm");
        mTargetResLabel.setText(projectImageSize(info.getImageSizePx(),
                info.getDrawSizeCm(), mDPI)
                + " px");
        mOriginalSizeLabel.setText(fileSizeToString(info.getImageSize()));
        mImageDetailsGroup.setVisible(true);
    }
}
