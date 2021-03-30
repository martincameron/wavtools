
package wavtools;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/* IMA-style 4-bit ADPCM Codec. */
public class AdpcmSampleData implements SampleData {
	private static final String VERSION = "20210330 (c) mumart@gmail.com";

	private static final int BUF_SAMPLES = 1 << 16;
	private static final int FP_SHIFT = 8, FP_ONE = 1 << FP_SHIFT;

	private static final int[] STEP = {
		384, 352, 320, 288, 224, 224, 224, 224,
		224, 224, 224, 224, 288, 320, 352, 384
	};

	private static final int[] BIAS = {
		// Used to bias the prediction based on the previous encoded value.
		-7,-6,-5,-4,-3,-2,-1,0,0,1,2,3,4,5,6,7
	};

	private byte[] inputBuf;
	private int[] preds, steps;
	private InputStream inputStream;
	private int numChannels, sampleRate, samplesRemaining;

	/* Encode the contents of specified SampleData to ADPCM and write to the specified OutputStream. */
	public static void encode( SampleData sampleData, OutputStream outputStream ) throws Exception {
		int numChannels = sampleData.getNumChannels();
		short[] inputBuf = new short[ BUF_SAMPLES * numChannels ];
		byte[] outputBuf = new byte[ BUF_SAMPLES * numChannels / 2 ];
		int[] preds = new int[ numChannels ];
		int[] steps = new int[ numChannels ];
		int count = 0, remain = 0;
		while( sampleData.getSamplesRemaining() > 0 ) {
			System.arraycopy( inputBuf, count * numChannels, inputBuf, 0, remain * numChannels );
			count = remain + sampleData.getSamples( inputBuf, remain, BUF_SAMPLES - remain );
			remain = count & 1;
			count = count & -2;
			for( int channel = 0; channel < numChannels; channel++ ) {
				int pred = preds[ channel ];
				int step = steps[ channel ];
				int bufferIdx = channel;
				int bufferEnd = count * numChannels + channel;
				while( bufferIdx < bufferEnd ) {
					if( step < FP_ONE ) step = FP_ONE;
					int delta = ( inputBuf[ bufferIdx ] << FP_SHIFT ) - pred;
					int code = ( 2 * delta + 15 * step ) / step;
					code = ( code & 1 ) + ( code >> 1 );
					if( code < 0 ) code = 0;
					if( code > 15 ) code = 15;
					inputBuf[ bufferIdx ] = ( short ) code;
					pred = pred + ( ( ( ( code << 1 ) - 15 ) * step ) >> 1 );
					pred = pred + BIAS[ code ] * step;
					step = ( step * STEP[ code ] ) >> FP_SHIFT;
					bufferIdx += numChannels;
				}
				preds[ channel ] = pred;
				steps[ channel ] = step;
			}
			int inputIdx = 0, outputIdx = 0;
			int outputEnd = count * numChannels / 2;
			while( outputIdx < outputEnd ) {
				outputBuf[ outputIdx++ ] = ( byte ) ( ( inputBuf[ inputIdx ] << 4 ) | ( inputBuf[ inputIdx + 1 ] & 0xF ) );
				inputIdx += 2;
			}
			outputStream.write( outputBuf, 0, outputEnd );
		}
	}

	/* Prepare to decode ADPCM audio from the specified InputStream. */
	public AdpcmSampleData( InputStream inputStream, int numChannels, int sampleRate, int numSamples ) {
		this.inputStream = inputStream;
		this.numChannels = numChannels;
		this.sampleRate = sampleRate;
		// Ensure numSamples is even, as getSamples() will not return less than two samples.
		this.samplesRemaining = numSamples & -2;
		inputBuf = new byte[ BUF_SAMPLES * numChannels / 2 ];
		preds = new int[ numChannels ];
		steps = new int[ numChannels ];
	}

	public int getNumChannels() {
		return numChannels;
	}

	public int getSampleRate() {
		return sampleRate;
	}

	public int getSamplesRemaining() {
		return samplesRemaining;
	}

	/* Decode and return count samples of audio.
	   Fewer than count samples will be returned if count is not divisible by 2. */
	public int getSamples( short[] outputBuf, int offset, int count ) throws IOException {
		if( count > samplesRemaining ) {
			count = samplesRemaining;
		}
		if( count > BUF_SAMPLES ) {
			count = BUF_SAMPLES;
		}
		count = readFully( inputStream, inputBuf, ( count / 2 ) * numChannels ) * 2 / numChannels;
		int outputIdx = offset * numChannels;
		int inputIdx = 0, inputEnd = count * numChannels / 2;
		while( inputIdx < inputEnd ) {
			int a = inputBuf[ inputIdx++ ] & 0xFF;
			outputBuf[ outputIdx++ ] = ( short ) ( a >> 4 );
			outputBuf[ outputIdx++ ] = ( short ) ( a & 0xF );
		}
		for( int channel = 0; channel < numChannels; channel++ ) {
			int pred = preds[ channel ];
			int step = steps[ channel ];
			int bufferIdx = offset * numChannels + channel;
			int bufferEnd = ( offset + count ) * numChannels + channel;
			while( bufferIdx < bufferEnd ) {
				if( step < FP_ONE ) step = FP_ONE;
				int code = outputBuf[ bufferIdx ];
				pred = pred + ( ( ( ( code << 1 ) - 15 ) * step ) >> 1 );
				int out = pred >> FP_SHIFT;
				if( out < -32768 ) {
					outputBuf[ bufferIdx ] = -32768;
				} else if ( out > 32767 ) {
					outputBuf[ bufferIdx ] =  32767;
				} else {
					outputBuf[ bufferIdx ] = ( short ) out;
				}
				pred = pred + BIAS[ code ] * step;
				step = ( step * STEP[ code ] ) >> FP_SHIFT;
				bufferIdx += numChannels;
			}
			preds[ channel ] = pred;
			steps[ channel ] = step;
		}
		samplesRemaining -= count;
		return count;
	}

	private static int readFully( InputStream input, byte[] inputBuf, int inputBytes ) throws IOException {
		int inputIdx = 0, inputRead = 0;
		while( inputIdx < inputBytes && inputRead >= 0 ) {
			inputIdx += inputRead;
			inputRead = input.read( inputBuf, inputIdx, inputBytes - inputIdx );
		}
		return inputIdx;
	}

	public static void main( String[] args ) throws Exception {
		if( args.length != 2 ) {
			System.err.println( "4-bit ADPCM codec. Version " + VERSION );
			System.err.println( "  Encode: java " + AdpcmSampleData.class.getName() + " input.wav output.adpcm" );
			System.err.println( "  Decode: java " + AdpcmSampleData.class.getName() + " input.adpcm output.wav" );
			System.exit( 0 );
		}
		java.io.File inputFile = new java.io.File( args[ 0 ] );
		java.io.FileInputStream inputStream = new java.io.FileInputStream( inputFile );
		java.io.FileOutputStream outputStream = new java.io.FileOutputStream( args[ 1 ] ); 
		if( inputFile.getName().toLowerCase().endsWith( "wav" ) ) {
			// Encode.
			SampleData sampleData = new WavSampleData( inputStream );
			outputStream.write( ( byte ) sampleData.getNumChannels() );
			int sampleRate = sampleData.getSampleRate();
			outputStream.write( ( byte ) ( sampleData.getSampleRate() >> 16 ) );
			outputStream.write( ( byte ) ( sampleData.getSampleRate() >> 8 ) );
			outputStream.write( ( byte ) ( sampleData.getSampleRate() ) );
			AdpcmSampleData.encode( sampleData, outputStream );
		} else {
			// Decode.
			int numChannels = inputStream.read();
			int sampleRate = ( inputStream.read() << 16 ) | ( ( inputStream.read() & 0xFF ) << 8 ) | ( inputStream.read() & 0xFF );
			int numSamples = ( ( int ) inputFile.length() - 4 ) * 2 / numChannels;
			SampleData sampleData = new AdpcmSampleData( inputStream, numChannels, sampleRate, numSamples );
			WavSampleData.writeWav( sampleData, outputStream );
		}
		outputStream.close();
	}
}
