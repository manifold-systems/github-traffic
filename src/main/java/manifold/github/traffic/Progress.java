package manifold.github.traffic;

import java.util.Arrays;
import java.util.List;

/**
 * Progress bar to display while making a batch of remote calls.
 */
public class Progress {
    private static final List<String> PROGRESS_BAR = Arrays.asList(
            "▓-----",
            "▓▓----",
            "▓▓▓---",
            "▓▓▓▓--",
            "▓▓▓▓▓-",
            "▓▓▓▓▓▓",
            "-▓▓▓▓▓",
            "--▓▓▓▓",
            "---▓▓▓",
            "----▓▓",
            "-----▓",
            "----▓▓",
            "---▓▓▓",
            "--▓▓▓▓",
            "-▓▓▓▓▓",
            "▓▓▓▓▓▓",
            "▓▓▓▓▓-",
            "▓▓▓▓--",
            "▓▓▓---",
            "▓▓----"
    );

    private final String _msg;
    private int _progress;

    public Progress(String msg) {
        _msg = msg;
        _progress = 0;
        System.out.print(msg);
    }

    public void bumpProgress() {
        String bar = progressBar();
        if (_progress >= 1) {
            System.out.print("\b".repeat(bar.length()));
        }
        System.out.print(bar);
        _progress++;
    }

    public void clearProgress() {
        int eraseLen = progressBar().length() + _msg.length();
        System.out.print("\b \b".repeat(eraseLen));
    }

    private String progressBar() {
        return PROGRESS_BAR.get(_progress % PROGRESS_BAR.size());
    }
}
