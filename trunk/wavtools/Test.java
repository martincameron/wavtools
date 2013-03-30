
package wavtools;

import java.io.ByteArrayInputStream; 
import java.io.ByteArrayOutputStream;

public class Test {
	public static void main( String[] args ) throws Exception {
		testWavSampleData( 1, 1 );
		testWavSampleData( 1, 2 );
		testWavSampleData( 2, 1 );
		testWavSampleData( 2, 2 );
		testWavSampleData( 3, 1 );
		testWavSampleData( 3, 2 );
		testWavSampleData( 3, 3 );
		System.out.println( "OK" );
	}
	
	public static void testWavSampleData( int len, int channels ) throws Exception {
		short[] inputSamples = new short[ len * channels ];
		for( int idx = 0; idx < inputSamples.length; idx++ ) {
			inputSamples[ idx ] = ( short ) ( idx * 3 );
		}
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		WavSampleData.writeWav( new ArraySampleData( inputSamples, channels, 44100, len ), os );
		ByteArrayInputStream is = new ByteArrayInputStream( os.toByteArray() );
		short[] outputSamples = new ArraySampleData( new WavSampleData( is ) ).getArray();	
		int ilen = inputSamples.length;
		int olen = outputSamples.length;
		if( outputSamples.length != inputSamples.length ) {
			throw new Exception( "Output length " + olen + " should be " + ilen );
		}
		for( int idx = 0; idx < ilen; idx++ ) {
			int in = inputSamples[ idx ];
			int out = outputSamples[ idx ];
			if( in != out ) {
				throw new Exception( in + " should be " + out + " at index " + idx );
			}
		}
	}
}
