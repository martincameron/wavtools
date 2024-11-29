
package wavtools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/* WAV Reader and writer. */
public class WavSampleData implements SampleData {
	private static final int BUF_SAMPLES = 1 << 16;

	private byte[] inputBuf;
	private InputStream inputStream;
	private int numChannels, sampleRate, bytesPerSample, samplesRemaining;

	/* Write the contents of the specified SampleData instance to the specified OutputStream as a 16-bit WAV file. */
	public static void writeWav( SampleData sampleData, OutputStream outputStream ) throws Exception {
		int numChannels = sampleData.getNumChannels();
		int sampleRate = sampleData.getSampleRate();
		writeChars( outputStream, "RIFF".toCharArray(), 4 );
		writeInt( outputStream, numChannels * sampleData.getSamplesRemaining() * 2 + 36 ); // Wave chunk length.
		writeChars( outputStream, "WAVE".toCharArray(), 4 );
		writeChars( outputStream, "fmt ".toCharArray(), 4 );
		writeInt( outputStream, 16 ); // Format chunk length.
		writeShort( outputStream, 1 ); // PCM format.
		writeShort( outputStream, numChannels );
		writeInt( outputStream, sampleRate );
		writeInt( outputStream, numChannels * sampleRate * 2 ); // Bytes per sec.
		writeShort( outputStream, numChannels * 2  ); // Frame size.
		writeShort( outputStream, 16 ); // 16 bit.
		writeChars( outputStream, "data".toCharArray(), 4 );
		writeInt( outputStream, numChannels * sampleData.getSamplesRemaining() * 2 ); // PCM data length.
		// Write data.
		short[] inputBuf = new short[ numChannels * BUF_SAMPLES ];
		byte[] outputBuf = new byte[ inputBuf.length * 2 ];
		while( sampleData.getSamplesRemaining() > 0 ) {
			int outputLen = sampleData.getSamples( inputBuf, 0, BUF_SAMPLES ) * numChannels * 2;
			for( int outputIdx = 0; outputIdx < outputLen; outputIdx += 2 ) {
				int amp = inputBuf[ outputIdx >> 1 ];
				outputBuf[ outputIdx ] = ( byte ) amp;
				outputBuf[ outputIdx + 1 ] = ( byte ) ( amp >> 8 );
			}
			outputStream.write( outputBuf, 0, outputLen );
		}
	}

	/* Prepare to decode a WAV file from the specified InputStream. */
	public WavSampleData( InputStream inputStream ) throws IOException {
		this.inputStream = inputStream;
		char[] chunkId = new char[ 4 ];
		readChars( inputStream, chunkId, 4 );
		if( !"RIFF".equals( new String( chunkId ) ) ) {
			throw new IllegalArgumentException( "Riff header not found." );
		}
		int chunkSize = readInt( inputStream );
		readChars( inputStream, chunkId, 4 );
		if( !"WAVE".equals( new String( chunkId ) ) ) {
			throw new IllegalArgumentException( "Wave header not found." );
		}
		readChars( inputStream, chunkId, 4 );
		if( !"fmt ".equals( new String( chunkId ) ) ) {
			throw new IllegalArgumentException( "Format header not found." );
		}
		chunkSize = readInt( inputStream );
		int format = readShort( inputStream );
		numChannels = readShort( inputStream );
		sampleRate = readInt( inputStream );
		int bytesPerSec = readInt( inputStream );
		bytesPerSample = readShort( inputStream );
		int bitsPerSample = readShort( inputStream );
		if( bitsPerSample > 24 ) {
			format = 0;
		}
		if( format == 0xFFFE ) {
			int blockSize = readShort( inputStream );
			int validBits = readShort( inputStream );
			int channelMask = readInt( inputStream );
			char[] formatId = new char[ 16 ];
			readChars( inputStream, formatId, 16 );
			String pcmId = "\u0001\u0000\u0000\u0000\u0000\u0000\u0010\u0000\u0080\u0000\u0000\u00AA\u0000\u0038\u009B\u0071";
			format = pcmId.equals( new String( formatId ) ) ? 1 : 0;
			inputStream.skip( chunkSize - 40 );
		} else {
			inputStream.skip( chunkSize - 16 );
		}
		if( format != 1 ) {
			throw new IllegalArgumentException( "Unsupported sample format." );
		}
		readChars( inputStream, chunkId, 4 );
		while( !"data".equals( new String( chunkId ) ) ) {
			//System.err.println( "Ignoring chunk: " + new String( chunkId ) );
			chunkSize = readInt( inputStream );
			inputStream.skip( chunkSize );
			readChars( inputStream, chunkId, 4 );
		}
		samplesRemaining = readInt( inputStream ) / bytesPerSample;
		inputBuf = new byte[ BUF_SAMPLES * bytesPerSample ];
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

	/* Read and convert at most count samples of audio data into the specified buffer. */
	public int getSamples( short[] outputBuf, int offset, int count ) throws IOException {
		if( count > samplesRemaining ) {
			count = samplesRemaining;
		}
		if( count > BUF_SAMPLES ) {
			count = BUF_SAMPLES;
		}
		count = readFully( inputStream, inputBuf, count * bytesPerSample ) / bytesPerSample;
		int inputIdx = 0, outputIdx = offset * numChannels, outputEnd = ( offset + count ) * numChannels;
		switch( bytesPerSample / numChannels ) {
			case 1: // 8-bit unsigned.
				while( outputIdx < outputEnd ) {
					outputBuf[ outputIdx++ ] = ( short ) ( ( ( inputBuf[ inputIdx++ ] & 0xFF ) - 128 ) << 8 );
				}
				break;
			case 2: // 16-bit signed little-endian.
				while( outputIdx < outputEnd ) {
					outputBuf[ outputIdx++ ] = ( short ) ( ( inputBuf[ inputIdx ] & 0xFF ) | ( inputBuf[ inputIdx + 1 ] << 8 ) );
					inputIdx += 2;
				}
				break;
			case 3: // 24-bit signed little-endian.
				while( outputIdx < outputEnd ) {
					outputBuf[ outputIdx++ ] = ( short ) ( ( inputBuf[ inputIdx + 1 ] & 0xFF ) | ( inputBuf[ inputIdx + 2 ] << 8 ) );
					inputIdx += 3;
				}
				break;
		}
		samplesRemaining -= count;
		return count;
	}

	private static int readInt( InputStream input ) throws IOException {
		return readShort( input ) | ( readShort( input ) << 16 );
	}
	
	private static void writeInt( OutputStream output, int value ) throws IOException {
		writeShort( output, value );
		writeShort( output, value >> 16 );
	}
	
	private static int readShort( InputStream input ) throws IOException {
		return ( input.read() & 0xFF ) | ( ( input.read() & 0xFF ) << 8 );
	}
	
	private static void writeShort( OutputStream output, int value ) throws IOException {
		output.write( ( byte ) value );
		output.write( ( byte ) ( value >> 8 ) );
	}
	
	private static void readChars( InputStream input, char[] chars, int length ) throws IOException {
		for( int idx = 0; idx < length; idx++ ) {
			chars[ idx ] = ( char ) ( input.read() & 0xFF );
		}
	}
	
	private static void writeChars( OutputStream output, char[] chars, int length ) throws IOException {
		for( int idx = 0; idx < length; idx++ ) {
			output.write( ( byte ) chars[ idx ] );
		}
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
		if( args.length != 4 ) {
			System.err.println( "Wave cropping tool.\nUsage: " + WavSampleData.class.getName() + " input.wav offset length output.wav");
			System.exit( 0 );
		}
		File inputFile = new File( args[ 0 ] );
		int offset = Integer.parseInt( args[ 1 ] );
		int length = Integer.parseInt( args[ 2 ] );
		File outputFile = new File( args[ 3 ] );
		ArraySampleData arraySampleData;
		try( InputStream inputStream = new FileInputStream( inputFile ) ) {
			arraySampleData = new ArraySampleData( new WavSampleData( inputStream ) );
		}
		if( length < 1 || offset + length > arraySampleData.getSamplesRemaining() ) {
			length = arraySampleData.getSamplesRemaining() - offset;
		}
		int numChannels = arraySampleData.getNumChannels();
		short[] array = new short[ length * numChannels ];
		System.arraycopy( arraySampleData.getArray(), offset * numChannels, array, 0, length * numChannels );
		arraySampleData = new ArraySampleData( array, numChannels, arraySampleData.getSampleRate(), length );
		try( OutputStream outputStream = new FileOutputStream( outputFile ) ) {
			writeWav( arraySampleData, outputStream );
		}
	}
}
