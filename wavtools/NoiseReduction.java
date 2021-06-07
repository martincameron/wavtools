
package wavtools;

/**
	An analogue-style noise-reduction algorithm based on DNR,
	using a variable 6db/octave low-pass filter.
*/
public class NoiseReduction implements SampleData {
	private static final float FC_MIN_HZ = 5;
	private static final float FC_MAX = ( float ) ( 2.0 * Math.PI );
	private static final float ATTACK_MS_PER_OCTAVE = 0.5f;
	private static final float RELEASE_MS_PER_OCTAVE = 50f;

	private SampleData input;

	private float floor, attack, release, fcMin, fc, hpX, hpY, lpY[];

	/**
		Constructor.
		@param input the input audio.
		@param dynamicRange the dynamic range in db (typically 54, lower values increase noise reduction).
	*/
	public NoiseReduction( SampleData input, int dynamicRange ) {
		this.input = input;
		lpY = new float[ input.getNumChannels() ];
		floor = ( float ) ( 32768 * Math.pow( 10, dynamicRange / -20.0 ) * input.getNumChannels() );
		attack = ( float ) Math.pow( 2, 1000 / ( input.getSampleRate() * ATTACK_MS_PER_OCTAVE ) );
		release = ( float ) Math.pow( 2, -1000 / ( input.getSampleRate() * RELEASE_MS_PER_OCTAVE ) );
		fcMin = fc = ( float ) ( 2.0 * Math.PI * FC_MIN_HZ / input.getSampleRate() );
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
			float ctrl = 0, alpha = ( float ) ( fc / ( fc + 1 ) );
			for( int chn = 0; chn < numChannels; chn++ ) {
				int idx = offset * numChannels + chn;
				lpY[ chn ] += alpha * ( buffer[ idx ] - lpY[ chn ] );
				ctrl += buffer[ idx ];
				float out = lpY[ chn ];
				if( out > 32767 ) {
					buffer[ idx ] = 32767;
				} else if( out < -32768 ) {
					buffer[ idx ] = -32768;
				} else {
					buffer[ idx ] = ( short ) out;
				}
			}
			hpY = ( hpY + ctrl - hpX ) / ( fc + 1 );
			hpX = ctrl;
			if( hpY > floor || -hpY > floor ) {
				fc *= attack;
				if( fc > FC_MAX ) {
					fc = FC_MAX;
				}
			} else {
				fc *= release;
				if( fc < fcMin ) {
					fc = fcMin;
				}
			}
			offset++;
		}
		return count;
	}
	
	public static void main( String[] args ) throws Exception {
		String input = null, output = null;
		int db = 54, idx = 0;
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
			System.err.println( "Usage: NoiseReduction [-db 54] input.wav output.wav" );
		}
	}
}
