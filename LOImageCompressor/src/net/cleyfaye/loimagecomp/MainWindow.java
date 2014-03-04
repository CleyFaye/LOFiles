package net.cleyfaye.loimagecomp;

import static net.cleyfaye.loimagecomp.utils.Utils.dataSizeToString;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;

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
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;

import net.cleyfaye.loimagecomp.imagecompress.Controller;
import net.cleyfaye.loimagecomp.imagecompress.Controller.SampleQuality;
import net.cleyfaye.loimagecomp.imagecompress.ImageCompress;
import net.cleyfaye.loimagecomp.imagecompress.ImageInfo;
import net.cleyfaye.loimagecomp.imagecompress.Interface;
import net.cleyfaye.loimagecomp.utils.ProgressCheck;

/**
 * Application main window
 * 
 * TODO Implement a controller somewhere to separate GUI and useful code
 * 
 * TODO Implement a CLI at some point
 * 
 * @author Cley Faye
 */
public class MainWindow implements Interface, ProgressCheck {

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
                // TODO move this out
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
                    mController.openFile(fc.getSelectedFile(), mDiz);
                    mOriginalSize = fc.getSelectedFile().length();
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
            if (!mController.isFileOpen()) {
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
                // TODO Maybe swingworker isn't the best choice here
                final SwingWorker<Integer, Integer> sw = new SwingWorker<Integer, Integer>() {

                    private boolean mResult;

                    @Override
                    protected Integer doInBackground() throws Exception
                    {
                        mResult = mController.saveFile(fc.getSelectedFile(),
                                mDiz);
                        return null;
                    }

                    @Override
                    protected void done()
                    {
                        if (mResult) {
                            final long previousSize = mOriginalSize;
                            final long newSize = fc.getSelectedFile().length();
                            JOptionPane.showMessageDialog(mframe,
                                    "Save complete. Initial file size: "
                                            + dataSizeToString(previousSize)
                                            + ", new size:"
                                            + dataSizeToString(newSize));
                        } else {
                            JOptionPane.showMessageDialog(mframe,
                                    "Operation aborted");
                        }
                    }

                };
                sw.execute();
            } catch (final Exception e2) {
                e2.printStackTrace();
            }
        }
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

    private final MainWindow mDiz = this;

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

    /** Controller */
    private final Controller mController = new ImageCompress(this);

    /**
     * Output DPI
     * 
     * TODO just implement a getter/setter for this that peek/poke the GUI
     */
    private double mDPI = 90;

    private List<ImageInfo> mImagesInfo = null;

    private ProgressMonitor mProgressMonitor;

    private long mOriginalSize;

    /**
     * Create the application.
     */
    public MainWindow() {
        initialize();
    }

    @Override
    public void endProgress()
    {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run()
                {
                    mProgressMonitor.close();
                    mProgressMonitor = null;
                    mframe.setEnabled(true);
                }
            });
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getJPEGQuality()
    {
        return ((Integer) mJpgQualitySpinner.getValue()).intValue();
    }

    @Override
    public boolean getKillTransparency()
    {
        return mKillTransparencyCheck.isSelected();
    }

    @Override
    public SampleQuality getSampleQuality()
    {
        switch (mScalingMethodCombo.getSelectedIndex()) {
        default:
        case 0:
            return SampleQuality.SQ_FAST;
        case 1:
            return SampleQuality.SQ_SMOOTH;
        }
    }

    @Override
    public double getTargetDPI()
    {
        return ((Float) mTargetDPISpinner.getValue()).doubleValue();
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

    @Override
    public boolean progress(final int value)
    {
        final boolean res[] = new boolean[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run()
                {
                    mProgressMonitor.setProgress(value);
                    res[0] = !mProgressMonitor.isCanceled();
                }
            });
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        return res[0];
    }

    @Override
    public boolean progressMessage(final String message)
    {
        final boolean res[] = new boolean[1];
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run()
                {
                    mProgressMonitor.setNote(message);
                    res[0] = !mProgressMonitor.isCanceled();
                }
            });
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        return res[0];
    }

    private void refreshImagesList()
    {
        mImageDetailsGroup.setVisible(false);
        final DefaultListModel<String> model = new DefaultListModel<>();
        for (int imgCount = 0; imgCount < mImagesInfo.size(); ++imgCount) {
            final ImageInfo info = mImagesInfo.get(imgCount);
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
        final ImageInfo info = mImagesInfo.get(index);
        if (!info.isEmbedded()) {
            mImageDetailsGroup.setVisible(false);
            return;
        }
        mOriginalResLabel.setText(info.getImageSizePx() + " px");
        mImageSizeLabel.setText(info.getDrawSizeCm() + " cm");
        mTargetResLabel.setText(ImageSize.projectImageSize(
                info.getImageSizePx(), info.getDrawSizeCm(), mDPI)
                + " px");
        mOriginalSizeLabel.setText(dataSizeToString(info.getImageSize()));
        mImageDetailsGroup.setVisible(true);
    }

    @Override
    public void startProgress(final String title, final int maxValue)
    {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {

                @Override
                public void run()
                {
                    mframe.setEnabled(false);
                    mProgressMonitor = new ProgressMonitor(mframe, title, "",
                            0, maxValue);
                }
            });
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void updateImagesList(final List<ImageInfo> images)
    {
        mImagesInfo = images;
        refreshImagesList();
    }

}
