
package wavtools;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/* 8-Bit Differential Companding Codec. */
public class CompressedSampleData implements SampleData {
	private static final String VERSION = "20241202 (c) mumart@gmail.com";

	private static final int BUF_SAMPLES = 1 << 16;

	private byte[] inputBuf;
	private int[] channelState;
	private InputStream inputStream;
	private int numChannels, sampleRate, samplesRemaining;

	public static int cbrt( int x ) {
		// Approximate cube-root of x. Halley's Method.
		int y = ( x >> 31 ) ^ 0xF;
		y = y * ( y * y * y + x + x ) / ( 2 * y * y * y + x );
		y = y * ( y * y * y + x + x ) / ( 2 * y * y * y + x );
		y = y * ( y * y * y + x + x ) / ( 2 * y * y * y + x );
		y = y * ( y * y * y + x + x ) / ( 2 * y * y * y + x );
		return y;
	}

	/* Encode the contents of specified SampleData and write to the specified OutputStream. */
	public static void encode( SampleData sampleData, OutputStream outputStream ) throws Exception {
		int numChannels = sampleData.getNumChannels();
		int[] channelState = new int[ numChannels ];
		short[] inputBuf = new short[ BUF_SAMPLES * numChannels ];
		byte[] outputBuf = new byte[ BUF_SAMPLES * numChannels ];
		int count = 0;
		while( sampleData.getSamplesRemaining() > 0 ) {
			count = sampleData.getSamples( inputBuf, 0, BUF_SAMPLES );
			for( int channel = 0; channel < numChannels; channel++ ) {
				int out = channelState[ channel ];
				int bufferIdx = channel;
				int bufferEnd = count * numChannels + channel;
				while( bufferIdx < bufferEnd ) {
					int in = cbrt( ( inputBuf[ bufferIdx ] - out ) << 5 );
					outputBuf[ bufferIdx ] = ( byte ) in;
					out += ( in * in * in ) >> 5;
					bufferIdx += numChannels;
				}
				channelState[ channel ] = out;
			}
			outputStream.write( outputBuf, 0, count * numChannels );
		}
	}

	/* Prepare to decode compressed audio from the specified InputStream. */
	public CompressedSampleData( InputStream inputStream, int numChannels, int sampleRate, int numSamples ) {
		this.inputStream = inputStream;
		this.numChannels = numChannels;
		this.sampleRate = sampleRate;
		this.samplesRemaining = numSamples;
		inputBuf = new byte[ BUF_SAMPLES * numChannels ];
		channelState = new int[ numChannels ];
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
			int out = channelState[ channel ];
			int inputIdx = channel;
			int inputEnd = count * numChannels + channel;
			int outputIdx = offset * numChannels + channel;
			while( inputIdx < inputEnd ) {
				int in = inputBuf[ inputIdx ];
				out += ( in * in * in ) >> 5;
				if( out < -32768 ) {
					outputBuf[ outputIdx ] = -32768;
				} else if( out > 32767 ) {
					outputBuf[ outputIdx ] = 32767;
				} else {
					outputBuf[ outputIdx ] = ( short ) out;
				}
				outputIdx += numChannels;
				inputIdx += numChannels;
			}
			channelState[ channel ] = out;
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
			String clsName = CompressedSampleData.class.getName();
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
			CompressedSampleData.encode( sampleData, outputStream );
		} else {
			// Decode.
			int numChannels = inputStream.read();
			int sampleRate = ( inputStream.read() << 16 ) | ( ( inputStream.read() & 0xFF ) << 8 ) | ( inputStream.read() & 0xFF );
			int numSamples = ( ( int ) inputFile.length() - 4 ) / numChannels;
			SampleData sampleData = new CompressedSampleData( inputStream, numChannels, sampleRate, numSamples );
			WavSampleData.writeWav( sampleData, outputStream );
		}
		outputStream.close();
	}
}
