package dev.becerritoo.minemories.fabric;

public record PhotoData(byte[] pngBytes, int width, int height, long capturedAtEpochMillis,
                        String cameraCaptureToken) {
    public PhotoData {
        pngBytes = pngBytes.clone();
        cameraCaptureToken = cameraCaptureToken == null ? "" : cameraCaptureToken;
    }

    public PhotoData(byte[] pngBytes, int width, int height, long capturedAtEpochMillis) {
        this(pngBytes, width, height, capturedAtEpochMillis, "");
    }

    public boolean cameraCapture() {
        return !cameraCaptureToken.isBlank();
    }

    @Override
    public byte[] pngBytes() {
        return pngBytes.clone();
    }
}
