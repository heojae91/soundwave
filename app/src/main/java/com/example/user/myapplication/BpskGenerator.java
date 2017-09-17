package com.example.user.myapplication;

/**
 * Created by user on 2017-09-17.
 */

public class BpskGenerator
{
    /**
     * PSK31 has a symbol rate of 31.25 symbols per second.
     * 우리가 하는건 PSK31을 쓰지 않을거니까 없애도 됨
     */
    public static final double PSK31_SYMBOLS_PER_SECOND = 31.25;

    /**
     * Default frequency of the audio tone to use.
     * 기본음을 1000 Hz짜리 쓰고 이걸 나중에 타겟주파수로 바꿀 예정
     */
    public static final double DEFAULT_FREQUENCY = 1000;

    /**
     * Default sample rate used to generate audio.
     * 이건 우리도 44100 그대로 사용할 예정
     */
    public static final int DEFAULT_SAMPLE_RATE = 44100;

    /**
     * The target frequency to generate or decode a PSK31 signal on.
     * 어디서 쓸껀지? 18 kHz 쓸꺼임
     */
    private double hz;

    /**
     * The audio sample rate. Typically 11025, 22050, or 44100.
     * 이거도 디폴트인 44100을 사용할 예정
     */
    private int sampleRate;

    /**
     * Current audio sample that this object will generate.
     *
     * To maintain a clean sinusoidal wave across multiple calls
     * to audio-generating methods in this class, the current
     * audio sample is maintained here. It starts at 0 and increases
     * over the life of this object.
     * 0부터 해서 샘플의 오프셋을 나타낸다
     */
    private long currentSample;

    /**
     * The number of radians a single audio sample represents.
     * 다음 샘플로 넘어갈때 얼마나 많은 phase change가 일어나는가에 대한 라디안값
     */
    private double radiansPerSample;

    /**
     * The rate that symbols are generated per second.
     *
     * This is used to compute how many samples to populate to represent a symbol.
     * 1 초동안 얼마나 많은 심볼이 나오는가?
     */
    private double symbolsPerSecond;

    /**
     * How many samples are required to encode a symbole?
     * 1심볼당 몇개의 샘플이 필요한가?
     */
    private double samplesPerSymbol;

    /**
     * Coefficients that are used to scale the start of a new symbol.
     * 심볼을 시작할 때 필터? 인듯
     */
    private double[] symbolStartFilter;

    /**
     * Coefficients that are used to scale the start of a new symbol.
     * 심볼 끝날때의 필터
     */
    private double[] symbolEndFilter;

    /**
     * The size of a single sample is currently always 2 bytes.
     * 이부분을 바꿔야 한다..
     */
    private static final int sampleSize = 16;

    /**
     * Constructor with sensible defaults.
     * <ul>
     * <li>{@link BpskGenerator#DEFAULT_FREQUENCY}</li>
     * <li>{@link BpskGenerator#DEFAULT_SAMPLE_RATE}</li>
     * <li>{@link BpskGenerator#PSK31_SYMBOLS_PER_SECOND}</li>
     * </ul>
     */
    public BpskGenerator() {
        this(DEFAULT_FREQUENCY, DEFAULT_SAMPLE_RATE, PSK31_SYMBOLS_PER_SECOND);
    }

    /**
     * Constructor.
     *
     * @param hz Frequency of carrier.
     * @param sampleRate The rate at which the signal is generated or recieved.
     */
    public BpskGenerator(final double hz, final int sampleRate) {
        this(hz, sampleRate, PSK31_SYMBOLS_PER_SECOND);
    }

    /**
     * Constructor.
     *
     * @param hz Frequency of the detected tone.
     * @param sampleRate The audio sample rate.
     * @param symbolsPerSecond How many symbols per second. For PSK31 this is {@link BpskGenerator#PSK31_SYMBOLS_PER_SECOND}.
     */
    public BpskGenerator(final double hz, final int sampleRate, double symbolsPerSecond) {
        this.hz               = hz;
        this.sampleRate       = sampleRate;
        this.symbolsPerSecond = symbolsPerSecond;
        this.samplesPerSymbol = this.sampleRate / this.symbolsPerSecond; // 샘플비율을 초로 나누어서 하나의 심볼을 만드는데 드는 샘플의 수를 파악함
        this.radiansPerSample = this.hz * 2.0 * Math.PI / (double)sampleRate; // 하나의 샘플이 넘어갈때 얼마나 많은 라디언값이 바뀌는가에 대한 식
        this.currentSample    = 0; // 처음 이니셜라이즈 할때는 일단 제로로 함

        /* Two 1/4 wave arrays to hold shaping coeeficients. */
        symbolStartFilter = new double[(int)(this.sampleRate / this.symbolsPerSecond / 2.0)]; // 하나의 심벌당 소요되는 샘플을 절반으로 나눈다
        symbolEndFilter   = new double[(int)(this.sampleRate / this.symbolsPerSecond / 2.0)]; // 스타트필터와 같음

        /* Cos filter the wave (yes, we use sin to generate the coefficients). */
        for (int i = 0; i < symbolStartFilter.length; ++i) {
            symbolStartFilter[i] = Math.sin(i * Math.PI / this.samplesPerSymbol); //
            symbolEndFilter[i]   = Math.cos(i * Math.PI / this.samplesPerSymbol);
        }
    }

    /**
     * Return the number of PSK symbols per second.
     *
     * @return the number of PSK symbols per second.
     */
    public double getSymbolRate() {
        return this.symbolsPerSecond;
    }

    /**
     * Generate a single symbol tone into the given buffer.
     * Writing begins at buf_i and continues to samplesPerSymbol
     * bytes are written into the array.
     * @param buffer The buffer to write into.
     * @param sample Which audio sample are we generating in buffer? This is
     *        converted into an index into buffer by the expression 2*sample.
     * @param shift The number of radians to shift the signal by.
     *        In PSK {@link Math.PI} will shift the signal 180
     *        degrees, turning the normal {@code 1} into {@code 0}.
     *
     * @return A new value for sample.
     */
    private int generateSignal(
            final byte[]  buffer,
            int           sample,
            final double  shift
    )
    {
        for (int i = 0; i < (int)samplesPerSymbol; ++i)
        {
            final double amplitude = Math.cos((radiansPerSample * currentSample++) + shift);

            short s = (short) (Short.MAX_VALUE * 0.8 * amplitude);

            buffer[2*sample] = (byte) ((s >>> 8) & 0xff);
            buffer[2*sample+1] = (byte) ((s) & 0xff);
            sample++;
        }

        return sample;
    }

    /**
     * Fade a buffer by applying a given filter.
     *
     * @param buffer The audio data to apply the filter to.
     * @param int sample The offset in the buffer to start processing at.
     * @param filter The filter to apply. Note that the length of the filter
     *        dictates how many samples will be read and modified.
     */
    private void fade(final byte[] buffer, int sample, double[] filter) {
        for (int i = 0; i < filter.length; i++) {

            final int buf_i = (sample + i) * 2;
            short s = (short)(((buffer[buf_i] << 8) & 0xff00) | (buffer[buf_i+1] & 0xff));

            s = (short)((double)s * filter[i]);

            buffer[buf_i] = (byte)((s>>>8)&0xff);
            buffer[buf_i+1] = (byte)((s)&0xff);
        }
    }

    /**
     * Fade in 1/2 a symbol worth of the buffer.
     *
     * @param buffer The buffer to write to.
     * @param buf_i The offset into buffer to start writing at.
     */
    private void fadeIn(final byte[] buffer, int sample) {
        fade(buffer, sample, symbolStartFilter);
    }

    /**
     * Fade out 1/2 a symbol worth of the buffer.
     *
     * @param buffer The buffer to write to.
     * @param buf_i The offset into buffer to start writing at.
     */
    private void fadeOut(final byte[] buffer, int sample) {
        fade(buffer, sample, symbolEndFilter);
    }

    /**
     * Calls {@link #generateSignal(byte[], int, int)}.
     *
     * @param symbols The list of 1s and 0s to generate a signal for.
     *
     * @throws IOException on errors reading internal buffers.
     * @return raw audio data that represents an encoding of the given symbols.
     */
    public byte[] generateSignal(final byte[] symbols) throws IOException {
        return generateSignal(symbols, 0, symbols.length);
    }

    /**
     * Given an array of 1s or 0s this will generate a PSK audio array.
     *
     * @param symbols The array of 1s and 0s. Any other value is an error.
     * @param off Offset into sybols into which to start processing.
     * @param len The length (number) of symbols to process.
     *
     * @throws IOException if a value in {@code symbols} is not a 1 or 0.
     * @return Array representing and audio segment of modulated PSK.
     */
    public byte[] generateSignal(final byte[] symbols, final int off, final int len) throws IOException {

        if (len == 0 ) {
            return new byte[0];
        }

        /* How many audio samples must we generate? */
        final int    samples = (int)(len * (int)samplesPerSymbol);

        /* We generate 16 bit audio, so there are 2 bytes per sample. */
        final byte[] buffer  = new byte[2 * samples];

        /* The previous symbol is always different than the very first symbol we get in symbols[]. */
        double shift      = 0;
        int    sample     = 0;

        sample = generateSignal(buffer, sample, shift);

        for (int sym_i = 1; sym_i < len; ++sym_i) {

            final byte symbol = symbols[off+sym_i];
            if (symbol == 0) {
                shift = (shift == 0) ? Math.PI : 0;

                fadeOut(buffer, (int)(sample - samplesPerSymbol/2));

                sample = generateSignal(buffer, sample, shift);

                fadeIn(buffer, (int)(sample - samplesPerSymbol));
            }
            else if (symbol == 1) {
                sample = generateSignal(buffer, sample, shift);
            }
            else {
                throw new IOException("Symbol at index "+sym_i+" was not a 1 or 0.");
            }
        }

        /* Fade in at the start. */
        fadeIn(buffer, 0);

        /* Fade out at the end. */
        fadeOut(buffer, samples - symbolEndFilter.length);

        return buffer;
    }

    /**
     * Return the audio format this class generates.
     *
     * @return the audio format this class generates.
     */
    AudioFormat getAudioFormat() {
        /* Do not change this. This class produces big-endian 16bit signed audio. */
        return new AudioFormat(sampleRate, sampleSize, 1, true, true);
    }

    /**
     * Return the size of a single audio frame.
     *
     * An audio frame is one sample * the number of channels. There is 1 channel in PSK
     * so this is the byte width of that single channel.
     */
    public int getFrameSize(){ return sampleSize/8; }

    /**
     * Return the sample rate.
     * @return The sample rate.
     */
    public int getSampleRate() { return sampleRate; }

}
