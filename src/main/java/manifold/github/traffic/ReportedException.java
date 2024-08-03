package manifold.github.traffic;

public class ReportedException extends RuntimeException {
    public ReportedException(String msg, RuntimeException e) {
        super(msg, e);

    }

}
