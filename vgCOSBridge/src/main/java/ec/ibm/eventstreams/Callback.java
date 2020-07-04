package ec.ibm.eventstreams;
/**
 * Helper interface used by the consumer to notify about incomming messages
 */
public interface Callback {
	/**
	 * Method invoked when a new message is received. The message value is passed as String
	 * @param payload - Message value
	 */
	public void callback(String payload);

}
