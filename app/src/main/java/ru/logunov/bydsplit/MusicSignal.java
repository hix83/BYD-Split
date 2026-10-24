package ru.logunov.bydsplit;

/** Visualizer FFT is signed complex data; DC and Nyquist are not music bands. */
final class MusicSignal {
    static final int BANDS = 24, POINTS = 96;
    static float[] spectrum(byte[] fft) {
        float[] result = new float[BANDS];
        if (fft == null || fft.length < 8) return result;
        int bins = fft.length / 2;
        for (int band = 0; band < BANDS; band++) {
            int from = Math.max(1, (int)Math.pow(bins, band / (double)BANDS));
            int to = Math.min(bins, Math.max(from + 1, (int)Math.pow(bins, (band + 1) / (double)BANDS)));
            double energy = 0;
            for (int bin = from; bin < to; bin++) {
                double re = fft[bin * 2], im = fft[bin * 2 + 1];
                energy += re * re + im * im;
            }
            result[band] = (float)Math.min(1, Math.sqrt(energy / Math.max(1, to - from)) / 48);
        }
        return result;
    }
    static float[] waveform(byte[] data) {
        float[] result = new float[POINTS];
        if (data == null || data.length == 0) return result;
        float mean = 0;
        for (byte value : data) mean += value & 255;
        mean /= data.length;
        for (int i = 0; i < POINTS; i++)
            result[i] = Math.max(-1, Math.min(1, ((data[i * (data.length - 1) / (POINTS - 1)] & 255) - mean) / 128f));
        return result;
    }
}
