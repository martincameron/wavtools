
package wavtools;

/**
	An analogue-style noise-reduction algorithm based on DNR,
	using a variable 12db/octave low-pass filter.
*/
public class NoiseReduction implements SampleData {
	private static final int OVERSAMPLE = 8;

	private static final int FREQ_MIN_HZ = 500;
	private static final float FREQ_MAX = ( float ) ( 2.0 * Math.PI * 0.5 / OVERSAMPLE );
	
	private static final float ATTACK_MS_PER_OCTAVE = 0.5f;
	private static final float RELEASE_MS_PER_OCTAVE = 50f;

	private SampleData input;

	private float[] s0, s1;
	
	private float floor, attack, release, freqMin, freq;

	/**
		Constructor.
		@param input the input audio.
		@param dynamicRange the dynamic range in db (typically 48, lower values increase noise reduction).
	*/
	public NoiseReduction( SampleData input, int dynamicRange ) {
		this.input = input;
		s0 = new float[ input.getNumChannels() ];
		s1 = new float[ input.getNumChannels() ];
		floor = ( float ) ( 32768 * Math.pow( 10, dynamicRange / -20.0 ) );
		attack = ( float ) Math.pow( 2, 1000 / ( input.getSampleRate() * ATTACK_MS_PER_OCTAVE ) );
		release = ( float ) Math.pow( 2, -1000 / ( input.getSampleRate() * RELEASE_MS_PER_OCTAVE ) );
		freqMin = freq = ( float ) ( 2.0 * Math.PI * FREQ_MIN_HZ / ( input.getSampleRate() * OVERSAMPLE ) );
	}

	public int getNumChannels() {
		return input.getNumChannels();
	}
	
	public int getSampleRate() {
		return input.getSampleRate();
	}
	
	public int getSamplesRemaining() {
		return input.getSamplesRemaining();
	}
	
	public int getSamples( short[] buffer, int offset, int count ) throws Exception {
		int numChannels = input.getNumChannels();
		count = input.getSamples( buffer, offset, count );
		int end = offset + count;
		while( offset < end ) {
			float hp = 0, lp = 0, ctrl = 0;
			for( int chn = 0; chn < numChannels; chn++ ) {
				int idx = offset * numChannels + chn;
				for( int os = 0; os < OVERSAMPLE; os++ ) {
					lp = s1[ chn ] + freq * s0[ chn ];
					hp = buffer[ idx ] - lp - /* res (1->0) * */ s0[ chn ];
					s0[ chn ] = freq * hp + s0[ chn ];
					s1[ chn ] = lp;
				}
				if( lp > 32767 ) {
					buffer[ idx ] = 32767;
				} else if( lp < -32768 ) {
					buffer[ idx ] = -32768;
				} else {
					buffer[ idx ] = ( short ) lp;
				}
				if( hp > ctrl ) {
					ctrl = hp;
				} else if( -hp > ctrl ) {
					ctrl = -hp;
				}
			}
			if( ctrl >= floor ) {
				freq *= attack;
				if( freq > FREQ_MAX ) {
					freq = FREQ_MAX;
				}
			} else {
				freq *= release;
				if( freq < freqMin ) {
					freq = freqMin;
				}
			}
			offset++;
		}
		return count;
	}
	
	public static void main( String[] args ) throws Exception {
		String input = null, output = null;
		int db = 48, idx = 0;
		while( idx < args.length ) {
			String arg = args[ idx++ ];
			if( "-db".equals( arg ) ) {
				db = Integer.parseInt( args[ idx++ ] );
			} else if( input == null ) {
				input = arg;
			} else if( output == null ) {
				output = arg;
			}
		}
		if( input != null && output != null ) {
			java.io.InputStream inputStream = new java.io.FileInputStream( input );
			try {
				java.io.OutputStream outputStream = new java.io.FileOutputStream( output );
				try {
					WavSampleData.writeWav( new NoiseReduction( new WavSampleData( inputStream ), db ), outputStream );
				}
				finally {
					outputStream.close();
				}
			}
			finally {
				inputStream.close();
			}
		} else {
			System.err.println( "Usage: NoiseReduction [-db 48] input.wav output.wav" );
		}
	}
}
