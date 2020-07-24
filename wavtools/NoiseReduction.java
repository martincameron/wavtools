
package wavtools;

/**
	An analogue-style noise-reduction algorithm,
	using a variable 12db/octave low-pass filter based on DNR.
*/
public class NoiseReduction implements SampleData {
	private static final int OVERSAMPLE = 8;

	private static final float F_MIN = ( float ) ( 2.0 * Math.PI * 0.01 / OVERSAMPLE );
	private static final float F_MAX = ( float ) ( 2.0 * Math.PI * 0.5 / OVERSAMPLE );

	private static final float ATTACK = ( F_MAX - F_MIN ) / 64;
	private static final float RELEASE = ( F_MAX - F_MIN ) / 4096;

	private SampleData input;

	private float[] s0, s1;
	
	private float trigger, freq = F_MIN;

	/**
		Constructor.
		@param input the input audio.
		@param dynamicRange the dynamic range in db (typically 56, lower values increase noise reduction).
	*/
	public NoiseReduction( SampleData input, int dynamicRange ) {
		this.input = input;
		trigger = ( float ) ( 32768 * Math.pow( 10, dynamicRange / -20.0 ) );
		s0 = new float[ input.getNumChannels() ];
		s1 = new float[ input.getNumChannels() ];
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
				buffer[ idx ] = ( short ) lp;
				if( hp > ctrl ) {
					ctrl = hp;
				} else if( -hp > ctrl ) {
					ctrl = -hp;
				}
			}
			if( ctrl >= trigger ) {
				freq += ATTACK;
				if( freq > F_MAX ) {
					freq = F_MAX;
				}
			} else {
				freq -= RELEASE;
				if( freq < F_MIN ) {
					freq = F_MIN;
				}
			}
			offset++;
		}
		return count;
	}
	
	public static void main( String[] args ) throws Exception {
		if( args.length == 2 ) {
			java.io.InputStream inputStream = new java.io.FileInputStream( args[ 0 ] );
			try {
				java.io.OutputStream outputStream = new java.io.FileOutputStream( args[ 1 ] );
				try {
					WavSampleData.writeWav( new NoiseReduction( new WavSampleData( inputStream ), 56 ), outputStream );
				}
				finally {
					outputStream.close();
				}
			}
			finally {
				inputStream.close();
			}
		} else {
			System.err.println( "Usage: NoiseReduction input.wav output.wav" );
		}
	}
}
