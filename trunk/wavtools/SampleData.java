
package wavtools;

/* An interface for streaming 16-bit audio data. */
public interface SampleData {
	/* Return the number of audio channels. */
	public int getNumChannels();
	/* Return the sample rate in hz. */
	public int getSampleRate();
	/* Return the number of samples remaining to be read. */
	public int getSamplesRemaining();
	/* Copy at most count samples of audio data into the specified buffer.
	   Offset and count correspond to one array index for each channel.
	   The number of samples placed in the buffer is returned, which may be less than count. */
	public int getSamples( short[] buffer, int offset, int count ) throws Exception;
}
