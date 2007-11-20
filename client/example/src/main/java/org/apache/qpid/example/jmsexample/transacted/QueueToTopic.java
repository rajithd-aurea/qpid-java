package org.apache.qpid.example.jmsexample.transacted;

import org.redhat.mrg.messaging.examples.BaseExample;

import javax.jms.*;

/**
 * Transactional message example sends a number of messages to a Queue
 * and then uses a transacted session to move them from the Queue to a Topic.
 * <p/>
 * <p>The program completes the following steps:
 * <ul>
 * <li>Publish the specified number of messages to the queue.</li>
 * <li>Within a transacted session consume all messages from the queue
 * and publish them to the topic.</li>
 * <li>By default commit the transacted session, unless the "<code>-rollback true</code>"
 * option is specified in which case roll it back.</li>
 * <li>Check for outstanding messages on the queue.</li>
 * <li>Check for outstanding messages on the topic.</li>
 * </ul>
 * <p/>
 */
public class QueueToTopic extends BaseExample
{
    /* Used in log output. */
    private static final String CLASS = "QueueToTopic";

    /* The queue name */
    private String _queueName;

    /* The topic name */
    private String _topicName;

    /* Specify if the transaction is committed */
    private boolean _commit;

    /**
     * Create a QueueToTopic client.
     *
     * @param args Command line arguments.
     */
    public QueueToTopic(String[] args)
    {
        super(CLASS, args);
        _queueName = _argProcessor.getStringArgument("-queueName");
        _topicName = _argProcessor.getStringArgument("-topicName");
        _commit = _argProcessor.getBooleanArgument("-commit");
    }

    /**
     * Run the message mover example.
     *
     * @param args Command line arguments.
     * @see BaseExample
     */
    public static void main(String[] args)
    {
        _options.put("-topicName", "The topic name");
        _defaults.put("-topicName", "world");
        _options.put("-queueName", "The queue name");
        _defaults.put("-queueName", "message_queue");
        _options.put("-commit", "Commit or rollback the transaction (true|false)");
        _defaults.put("-commit", "true");
        QueueToTopic mover = new QueueToTopic(args);
        mover.runTest();
    }

    private void runTest()
    {
        try
        {

            // Lookup the queue
            System.out.println(CLASS + ": Looking up queue with name: " + _queueName);
            Queue queue = (Queue) getInitialContext().lookup(_queueName);

            // Lookup the topic
            System.out.println(CLASS + ": Looking up topic with name: " + _topicName);
            Topic topic = (Topic) getInitialContext().lookup(_topicName);

            // Declare the connection
            Connection connection = getConnection();

            // As this application is using a MessageConsumer we need to set an ExceptionListener on the connection
            // so that errors raised within the JMS client library can be reported to the application
            System.out.println(
                    CLASS + ": Setting an ExceptionListener on the connection as sample uses a MessageConsumer");

            connection.setExceptionListener(new ExceptionListener()
            {
                public void onException(JMSException jmse)
                {
                    // The connection may have broken invoke reconnect code if available.
                    System.err.println(CLASS + ": The sample received an exception through the ExceptionListener");
                    System.err.println(
                            CLASS + ": If this was a real application it should now go through reconnect code");
                    System.err.println();
                    System.err.println("Exception: " + jmse);
                    System.err.println();
                    System.err.println("Now exiting.");
                    System.exit(0);
                }
            });

            // Start the connection
            connection.start();

            /**
             * Create nonTransactedSession. This non-transacted auto-ack session is used to create the MessageProducer
             * that is used to populate the queue and the MessageConsumer that is used to consume the messages
             * from the topic.
             */
            System.out.println(CLASS + ": Creating a non-transacted, auto-acknowledged session");
            Session nonTransactedSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Make sure that the queue is empty
            System.out.print(CLASS + ": Purging messages from queue...");
            MessageConsumer queueMessageConsumer = nonTransactedSession.createConsumer(queue);
            Message purgedMessage;
            int numberPurged = -1;
            do
            {
                purgedMessage = queueMessageConsumer.receiveNoWait();
                numberPurged++;
            }
            while (purgedMessage != null);
            System.out.println(numberPurged + " message(s) purged.");

            // Create the MessageProducer for the queue
            System.out.println(CLASS + ": Creating a MessageProducer for the queue");
            MessageProducer messageProducer = nonTransactedSession.createProducer(queue);

            // Now create the MessageConsumer for the topic
            System.out.println(CLASS + ": Creating a MessageConsumer for the topic");
            MessageConsumer topicMessageConsumer = nonTransactedSession.createConsumer(topic);

            // Create a textMessage. We're using a TextMessage for this example.
            System.out.println(CLASS + ": Creating a TestMessage to send to the destination");
            TextMessage textMessage = nonTransactedSession.createTextMessage("Sample text message");

            // Loop to publish the requested number of messages to the queue.
            for (int i = 1; i < getNumberMessages() + 1; i++)
            {
                messageProducer
                        .send(textMessage, getDeliveryMode(), Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);

                // Print out details of textMessage just sent
                System.out.println(CLASS + ": Message sent: " + i + " " + textMessage.getJMSMessageID());
            }

            // Create a new transacted Session to move the messages from the queue to the topic
            Session transactedSession = connection.createSession(true, Session.SESSION_TRANSACTED);

            // Create a new message consumer from the queue
            MessageConsumer transactedConsumer = transactedSession.createConsumer(queue);

            // Create a new message producer for the topic
            MessageProducer transactedProducer = transactedSession.createProducer(topic);

            // Loop to consume the messages from the queue and publish them to the topic
            Message receivedMessage;
            for (int i = 1; i < getNumberMessages() + 1; i++)
            {
                // Receive a message
                receivedMessage = transactedConsumer.receive();
                System.out.println(CLASS + ": Moving message: " + i + " " + receivedMessage.getJMSMessageID());
                // Publish it to the topic
                transactedProducer.send(receivedMessage);
            }

            // Either commit or rollback the transacted session based on the command line args.
            if (_commit)
            {
                System.out.println(CLASS + ": Committing transacted session.");
                transactedSession.commit();
            }
            else
            {
                System.out.println(CLASS + ": Rolling back transacted session.");
                transactedSession.rollback();
            }
        
            // Now consume any outstanding messages on the queue
            System.out.print(CLASS + ": Mopping up messages from queue");
            if (_commit)
            {
                System.out.print(" (expecting none)...");
            }
            else
            {
                System.out.print(" (expecting " + getNumberMessages() + ")...");
            }

            Message moppedMessage;
            int numberMopped = 0;
            do
            {
                moppedMessage = queueMessageConsumer.receiveNoWait();
                if( moppedMessage != null)
                {
                    numberMopped++;
                }
            }
            while (moppedMessage != null);
            System.out.println(numberMopped + " message(s) mopped.");

            // Now consume any outstanding messages for the topic subscriber
            System.out.print(CLASS + ": Mopping up messages from topic");

            if (_commit)
            {
                System.out.print(" (expecting " + getNumberMessages() + ")...");
            }
            else
            {
                System.out.print(" (expecting none)...");
            }

            numberMopped = 0;
            do
            {
                moppedMessage = topicMessageConsumer.receiveNoWait();
                if( moppedMessage != null)
                {
                    numberMopped++;
                }
            }
            while (moppedMessage != null);
            System.out.println(numberMopped + " message(s) mopped.");

            // Close the QueueConnection to the server
            System.out.println(CLASS + ": Closing connection");
            connection.close();

            // Close the JNDI reference
            System.out.println(CLASS + ": Closing JNDI context");
            getInitialContext().close();
        }
        catch (Exception exp)
        {
            System.err.println(CLASS + ": Caught an Exception: " + exp);
        }
    }
}
