package net.cleyfaye.loimagecomp.utils;

/**
 * Handle the display of a progress bar for long tasks
 * 
 * An object can implement this interface to display progress.
 * 
 * @author Cley Faye
 */
public interface ProgressCheck {

    /**
     * Proxy to use ProgressCheck even in case of a null value.
     * 
     * Instantiate an instance of this class to transparently use a
     * ProgressCheck, even if it's value is null (in this case, nothing is
     * displayed).
     * 
     * @author Cley Faye
     */
    public static class Instance implements ProgressCheck {
        private final ProgressCheck mProgressCheck;

        public Instance(final ProgressCheck progressCheck) {
            mProgressCheck = progressCheck;
        }

        @Override
        public void endProgress()
        {
            if (mProgressCheck != null) {
                mProgressCheck.endProgress();
            }
        }

        @Override
        public boolean progress(final int value)
        {
            if (mProgressCheck != null) {
                return mProgressCheck.progress(value);
            }
            return true;
        }

        @Override
        public boolean progressMessage(final String message)
        {
            if (mProgressCheck != null) {
                return mProgressCheck.progressMessage(message);
            }
            return true;
        }

        @Override
        public void startProgress(final String title, final int maxValue)
        {
            if (mProgressCheck != null) {
                mProgressCheck.startProgress(title, maxValue);
            }
        }

    }

    /**
     * End the progress bar display.
     * 
     * Called once at some point after a call to startProgress().
     */
    public void endProgress();

    /**
     * Change the progress value.
     * 
     * @param maxValue
     *            A value between 0 and the maximum value passed to
     *            startProgress().
     * @return true to continue, or false if the process must be interrupted
     */
    public boolean progress(int value);

    /**
     * Display a progress message.
     * 
     * @param message
     *            A new message to display.
     * @return true to continue, or false if the process must be interrupted
     */
    public boolean progressMessage(String message);

    /**
     * Called to display the progress bar.
     * 
     * This function will be called once per "progress". It will always be
     * followed by a call to endProgress() at some point. There should not be
     * two call to startProgress() without a corresponding call to
     * endProgress().
     * 
     * The progress() and progressMessage() functions can only be called between
     * a call to startProgress() and endProgress().
     * 
     * @param title
     *            A title that can be displayed to the user
     * @param maxValue
     *            The maximum progress value
     */
    public void startProgress(String title, int maxValue);
}
