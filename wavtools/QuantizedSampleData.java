
package wavtools;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/*
	Dynamic-quantizer which reduces the
	precision of high-amplitude portions of the signal.
	Can be used with PCM compression algorithms such as FLAC, to reduce
	the file-size by as much as half without significant loss of quality.
*/
public class QuantizedSampleData implements SampleData {
	private static final String VERSION = "20210401 (c) mumart@gmail.com";

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
		this( sampleData, 8 );
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
				int min = 0, max = 0;
				for( int idx = offset, endIdx = offset + samples; idx < endIdx; idx++ ) {
					int amp = outputBuf[ idx * channels + channel ];
					if( amp < min ) {
						min = amp;
					}
					if( amp > max ) {
						max = amp;
					}
				}
				/* Calculate the dynamic-range of this chunk. */
				int bits = 0;
				int variance = max - min;
				while( variance > 0 ) {
					bits++;
					variance >>= 1;
				}
				if( bits > precision ) {
					/* Quantize and round. */
					for( int idx = offset, endIdx = offset + samples; idx < endIdx; idx++ ) {
						int amp = ( outputBuf[ idx * channels + channel ] + 32768 ) >> ( bits - precision - 1 );
						amp = ( amp >> 1 ) + ( amp & 1 );
						outputBuf[ idx * channels + channel ] = ( short ) ( ( amp << ( bits - precision ) ) - 32768 );
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
