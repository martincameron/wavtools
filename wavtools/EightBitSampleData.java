
package wavtools;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/* Convert to 8-bit signed PCM with noise shaping. */
public class EightBitSampleData implements SampleData {
	private static final String VERSION = "20140201 (c) mumart@gmail.com";

	private static final int BUF_SAMPLES = 1 << 16;

	private byte[] inputBuf;
	private InputStream inputStream;
	private int numChannels, sampleRate, samplesRemaining;

	/* Encode the contents of specified SampleData and write to the specified OutputStream. */
	public static void encode( SampleData sampleData, OutputStream outputStream ) throws Exception {
		int numChannels = sampleData.getNumChannels();
		short[] inputBuf = new short[ BUF_SAMPLES * numChannels ];
		byte[] outputBuf = new byte[ BUF_SAMPLES * numChannels ];
		int count = 0;
		while( sampleData.getSamplesRemaining() > 0 ) {
			count = sampleData.getSamples( inputBuf, 0, BUF_SAMPLES );
			for( int channel = 0; channel < numChannels; channel++ ) {
				int in = 0, out = 0, rand = 0, s1 = 0, s2 = 0, s3 = 0;
				int bufferIdx = channel;
				int bufferEnd = count * numChannels + channel;
				while( bufferIdx < bufferEnd ) {
					// Convert to unsigned for proper integer rounding.
					in = inputBuf[ bufferIdx ] + 32768;
					// TPDF dither.
					rand = ( rand * 65 + 17 ) & 0x7FFFFFFF;
					int dither = rand >> 25;
					rand = ( rand * 65 + 17 ) & 0x7FFFFFFF;
					dither -= rand >> 25;
					// "F-weighted" 3-tap noise shaping. Works well around 32khz.
					in = in - ( s1 * 13 -s2 * 8 + s3 ) / 8 + dither;
					s3 = s2;
					s2 = s1;
					// Rounding and quantization.
					out = ( in + ( in & 0x80 ) ) >> 8;
					// Clipping.
					if( out < 0 ) out = 0;
					if( out > 255 ) out = 255;
					// Feedback.
					s1 = ( out << 8 ) - in;
					outputBuf[ bufferIdx ] = ( byte ) ( out - 128 );
					bufferIdx += numChannels;					
				}
			}
			outputStream.write( outputBuf, 0, count * numChannels );
		}
	}

	/* Prepare to decode 8-bit signed PCM audio from the specified InputStream. */
	public EightBitSampleData( InputStream inputStream, int numChannels, int sampleRate, int numSamples ) {
		this.inputStream = inputStream;
		this.numChannels = numChannels;
		this.sampleRate = sampleRate;
		this.samplesRemaining = numSamples;
		inputBuf = new byte[ BUF_SAMPLES * numChannels ];
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

	/* Decode and return count samples of audio. */
	public int getSamples( short[] outputBuf, int offset, int count ) throws IOException {
		if( count > samplesRemaining ) {
			count = samplesRemaining;
		}
		if( count > BUF_SAMPLES ) {
			count = BUF_SAMPLES;
		}
		count = readFully( inputStream, inputBuf, count * numChannels ) / numChannels;
		for( int channel = 0; channel < numChannels; channel++ ) {
			int inputIdx = channel;
			int inputEnd = count * numChannels + channel;
			int outputIdx = offset * numChannels + channel;
			while( inputIdx < inputEnd ) {
				outputBuf[ outputIdx ] = ( short ) ( inputBuf[ inputIdx ] << 8 );
				outputIdx += numChannels;
				inputIdx += numChannels;
			}
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
		// Codec for simple 8-bit PCM file format.
		if( args.length != 2 ) {
			String clsName = EightBitSampleData.class.getName();
			System.err.println( clsName + " Version " + VERSION );
			System.err.println( "  Encode: java " + clsName + " input.wav output.pcm" );
			System.err.println( "  Decode: java " + clsName + " input.pcm output.wav" );
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
			EightBitSampleData.encode( sampleData, outputStream );
		} else {
			// Decode.
			int numChannels = inputStream.read();
			int sampleRate = ( inputStream.read() << 16 ) | ( ( inputStream.read() & 0xFF ) << 8 ) | ( inputStream.read() & 0xFF );
			int numSamples = ( ( int ) inputFile.length() - 4 ) / numChannels;
			SampleData sampleData = new EightBitSampleData( inputStream, numChannels, sampleRate, numSamples );
			WavSampleData.writeWav( sampleData, outputStream );
		}
		outputStream.close();
	}
}
