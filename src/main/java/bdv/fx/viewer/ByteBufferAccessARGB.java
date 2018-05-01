package bdv.fx.viewer;

import java.nio.ByteBuffer;

import net.imglib2.img.basictypeaccess.IntAccess;

public class ByteBufferAccessARGB implements IntAccess
{

	private final ByteBuffer buffer;

	public ByteBufferAccessARGB( final ByteBuffer buffer )
	{
		this.buffer = buffer;
		buffer.array();
	}

	@Override
	public int getValue( final int index )
	{
		final int byteIndex = index * Integer.BYTES;
		final int argb =
				( buffer.get( byteIndex + 0 ) & 0xff ) << 0
			  | ( buffer.get( byteIndex + 1 ) & 0xff ) << 8
			  | ( buffer.get( byteIndex + 2 ) & 0xff ) << 16
			  | ( buffer.get( byteIndex + 3 ) & 0xff ) << 24;
		return argb;
	}

	@Override
	public void setValue( final int index, final int argb )
	{
		final int byteIndex = index * Integer.BYTES;
		buffer.put( byteIndex + 0, ( byte ) ( argb >>> 0 ) );
		buffer.put( byteIndex + 1, ( byte ) ( argb >>> 8 ) );
		buffer.put( byteIndex + 2, ( byte ) ( argb >>> 16 ) );
		buffer.put( byteIndex + 3, ( byte ) ( argb >>> 24 ) );
	}

}