package dev.becerritoo.minemories.client;

public record InstaxSettings(Filter filter, double sharpness, double contrast,
                             double saturation, boolean dithering) {
    public static final InstaxSettings SOFT = new InstaxSettings(Filter.BICUBIC,
            0.10, 1.00, 1.00, true);
    public static final InstaxSettings BALANCED = new InstaxSettings(Filter.LANCZOS,
            0.25, 1.015, 1.02, true);
    public static final InstaxSettings SHARP = new InstaxSettings(Filter.LANCZOS,
            0.50, 1.025, 1.04, true);

    public InstaxSettings {
        filter = filter == null ? Filter.LANCZOS : filter;
        sharpness = clamp(sharpness, 0.0, 1.0);
        contrast = clamp(contrast, 0.80, 1.20);
        saturation = clamp(saturation, 0.80, 1.20);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum Filter {
        BICUBIC("Bicúbico"),
        LANCZOS("Lanczos");

        private final String label;

        Filter(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public Filter next() {
            return this == BICUBIC ? LANCZOS : BICUBIC;
        }
    }

    public enum Preset {
        SOFT("Suave"),
        BALANCED("Equilibrado"),
        SHARP("Nítido"),
        CUSTOM("Personalizado");

        private final String label;

        Preset(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
