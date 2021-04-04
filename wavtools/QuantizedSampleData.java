
package wavtools;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/*
	Dynamic-quantizer which reduces the
	precision of unpredictable portions of the signal.
	Can be used with PCM compression algorithms such as FLAC, to reduce
	the file-size by as much as half without significant loss of quality.
*/
public class QuantizedSampleData implements SampleData {
	private static final String VERSION = "20210404 (c) mumart@gmail.com";

	private SampleData input;
	private int precision;

	/* Precision specified in bits per sample in the range 3 to 15. */
	public QuantizedSampleData( SampleData sampleData, int precision ) {
		if( precision < 3 || precision > 15 ) {
			throw new IllegalArgumentException( "Invalid precision parameter." );
		}
		input = sampleData;
		this.precision = precision;
	}

	public QuantizedSampleData( SampleData sampleData ) {
		this( sampleData, 5 );
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

	public int getSamples( short[] outputBuf, int offset, int count ) throws Exception {
		if( input.getSamplesRemaining() < count ) {
			count = input.getSamplesRemaining();
		}
		int end = offset + count;
		while( offset < end ) {
			/* Quantize chunks of at most 64 samples. */
			int samples = input.getSamples( outputBuf, offset, 64 );
			for( int channel = 0, channels = input.getNumChannels(); channel < channels; channel++ ) {
				/* Estimate the unpredictability of the signal. */
				int max = 0;
				for( int idx = offset + 1, endIdx = offset + samples; idx < endIdx; idx++ ) {
					int da = outputBuf[ idx * channels + channel ] - outputBuf[ ( idx - 1 ) * channels + channel ];
					if( da < 0 ) {
						da = -da;
					}
					if( da > max ) {
						max = da;
					}
				}
				/* Determine the number of bits to discard. */
				int bits = -precision;
				while( max > 0 ) {
					bits++;
					max >>= 1;
				}
				if( bits > 0 ) {
					/* Quantize and round. */
					for( int idx = offset, endIdx = offset + samples; idx < endIdx; idx++ ) {
						int amp = ( outputBuf[ idx * channels + channel ] + 32768 ) >> ( bits - 1 );
						amp = ( amp >> 1 ) + ( amp & 1 );
						outputBuf[ idx * channels + channel ] = ( short ) ( ( amp << bits ) - 32768 );
					}
				}
			}
			offset += samples;
		}
		return count;
	}

	public static void main( String[] args ) throws Exception {
		if( args.length != 2 ) {
			System.err.println( "Dynamic quantizer. Version " + VERSION );
			System.err.println( "  Usage: java " + QuantizedSampleData.class.getName() + " input.wav output.wav" );
			System.exit( 0 );
		}
		java.io.FileInputStream inputStream = new java.io.FileInputStream( args[ 0 ] );
		java.io.FileOutputStream outputStream = new java.io.FileOutputStream( args[ 1 ] ); 
		try {
			WavSampleData.writeWav( new QuantizedSampleData( new WavSampleData( inputStream ) ), outputStream );
		} finally {
			outputStream.close();
			inputStream.close();
		}
	}
}
