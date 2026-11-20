package android.content.pm;

public class PackageParser {
    public static class PackageParserException extends Exception {
        public final int error;
        public PackageParserException(int error, String detailMessage) {
            super(detailMessage);
            this.error = error;
        }
        public PackageParserException(int error, String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
            this.error = error;
        }
    }
}
