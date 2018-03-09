package net.trajano.ms.engine.internal;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Semaphore;

/**
 * <p></p>An input stream that wraps a Vert.X Read Buffer.  This does not do anything that performs blocks.</p>
 * <p>This pauses() the stream once the buffer is read and resumes the stream when the buffer is fully read.</p>
 */
public class VertxInputStream extends InputStream {

    private static final Buffer END_BUFFER = Symbol.newSymbol(Buffer.class);

    private static final Buffer END_BUFFER_WITH_ERROR = Symbol.newSymbol(Buffer.class);

    private static final Logger LOG = LoggerFactory.getLogger(VertxInputStream.class);
    /**
     * Read Stream being wrapped.
     */
    private final ReadStream<Buffer> readStream;
    /**
     * Semaphore to lock.  Permits is equivalent to available bytes.
     */
    private final Semaphore streamSemaphore = new Semaphore(0);
    private long bytesRead = 0;
    /**
     * Flag to indicate that the InputStream is closed.
     */
    private boolean closed = false;
    /**
     * Flag to indicate that the ReadStream is ended.
     */
    private boolean ended = false;
    /**
     * Current buffer.
     */
    private Buffer currentBuffer;
    /**
     * Current position in buffer.
     */
    private int pos;
    /**
     * Exception holder.  If this is not null, it will be thrown on the next read.
     */
    private IOException exceptionToThrow = null;

    public VertxInputStream(final ReadStream<Buffer> readStream) {

        this.readStream = readStream;
        readStream
            .handler(this::populate)
            .exceptionHandler(this::error)
            .endHandler(aVoid -> end());

    }

    @Override
    public int available() throws IOException {

        return streamSemaphore.availablePermits();
    }

    @Override
    public void close() throws IOException {

        closed = true;

    }

    /**
     * Signals that the readstream has ended.
     */
    public void end() {

        ended = true;

    }

    /**
     * End the buffer because of an error.
     *
     * @param e
     */
    public void error(final Throwable e) {

        exceptionToThrow = new IOException(e);

    }

    @Override
    public boolean markSupported() {

        return false;
    }

    /**
     * Sets the buffer with a new one from the stream then pauses.
     *
     * @param buffer buffer from stream.
     */
    public void populate(final Buffer buffer) {

        readStream.pause();
        currentBuffer = buffer;
        pos = 0;
        streamSemaphore.release(buffer.length());
    }

    @Override
    public int read() throws IOException {

        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }

        if (closed) {
            throw new IOException("Stream is closed");
        }

        try {
            if (!streamSemaphore.tryAcquire()) {
                System.out.println("semaphore exhausted " + bytesRead);
                readStream.resume();
                streamSemaphore.acquire();
            }
        } catch (InterruptedException e) {
            error(e);
            Thread.currentThread().interrupt();
        }
        if (ended) {
            return -1;
        }
        // Convert to unsigned byte
        final int b = currentBuffer.getByte(pos++) & 0xFF;
        ++bytesRead;

        return b;

    }

    @Override
    public synchronized void reset() throws IOException {

        throw new IOException("reset not supported");
    }

    /**
     * Gets a count of how much bytes have been read from this input stream.
     *
     * @return total bytes read
     */
    public long totalBytesRead() {

        return bytesRead;
    }

}
