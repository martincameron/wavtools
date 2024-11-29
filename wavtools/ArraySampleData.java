
package wavtools;

/* An in-memory SampleData implementation. */
public class ArraySampleData implements SampleData {
	private short[] inputBuf;
	private int numChannels, sampleRate, numSamples, inputOffset;

	public ArraySampleData( short[] array, int numChannels, int sampleRate, int numSamples ) {
		this.inputBuf = array;
		this.numChannels = numChannels;
		this.sampleRate = sampleRate;
		this.numSamples = numSamples;
	}

	public ArraySampleData( SampleData sampleData ) throws Exception {
		this.numChannels = sampleData.getNumChannels();
		this.sampleRate = sampleData.getSampleRate();
		this.numSamples = sampleData.getSamplesRemaining();
		this.inputBuf = new short[ numSamples * numChannels ];
		int offset = 0;
		while( sampleData.getSamplesRemaining() > 0 ) {
			offset += sampleData.getSamples( inputBuf, offset, numSamples - offset );
		}
	}
	
	public void setOffset( int offset ) {
		inputOffset = offset;
	}

	public short[] getArray() {
		return inputBuf;
	}
	
	public int getNumChannels() {
		return numChannels;
	}
	
	public int getSampleRate() {
		return sampleRate;
	}
	
	public int getSamplesRemaining() {
		return numSamples - inputOffset;
	}
	
	public int getSamples( short[] outputBuf, int outputOffset, int outputCount ) {
		if( outputCount > getSamplesRemaining() ) {
			outputCount = getSamplesRemaining();
		}
		System.arraycopy( inputBuf, inputOffset * numChannels, outputBuf, outputOffset * numChannels, outputCount * numChannels );
		inputOffset += outputCount;
		return outputCount;
	}
}
