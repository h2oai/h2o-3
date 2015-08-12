package h2o.testng.utils;

import java.util.Calendar;

public class RecordingTestcase {

	private Calendar datetimeStart = null;
	private Calendar datetimeEnd = null;
	private Runtime runtimeStart = null;
	private Runtime runtimeEnd = null;

	public RecordingTestcase() {

		datetimeStart = Calendar.getInstance();
		runtimeStart = Runtime.getRuntime();
	}

	public void startRecording() {

		datetimeStart = Calendar.getInstance();
		runtimeStart = Runtime.getRuntime();
	}

	/**
	 * set endRecording value. It is replaced if you call this function more one times.
	 */
	public void endRecording() {

		datetimeEnd = Calendar.getInstance();
		runtimeEnd = Runtime.getRuntime();
	}

	/**
	 * @return time of recording (millisecond)
	 */
	public long getTimeRecording() {

		if (datetimeEnd == null) {
			datetimeEnd = Calendar.getInstance();
		}

		return datetimeEnd.getTimeInMillis() - datetimeStart.getTimeInMillis();
	}

	/**
	 * @return memory is used in computer (bytes)
	 */
	public long getUsedMemory() {

		if (runtimeEnd == null) {
			runtimeEnd = Runtime.getRuntime();
		}

		return runtimeStart.freeMemory() - runtimeEnd.freeMemory();
	}
	
	public final static int MB = 1024*1024;
}
