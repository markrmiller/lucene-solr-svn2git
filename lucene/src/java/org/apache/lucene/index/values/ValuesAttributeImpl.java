package org.apache.lucene.index.values;

import java.util.Comparator;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.FloatsRef;
import org.apache.lucene.util.LongsRef;

public class ValuesAttributeImpl extends AttributeImpl implements ValuesAttribute {
  private Values type;
  private BytesRef bytes = null;
  private FloatsRef floats = null;
  private LongsRef ints = null;
  private Comparator<BytesRef> bytesComp;

  public BytesRef bytes() {
    return bytes;
  }

  public FloatsRef floats() {
    return floats;
  }

  public LongsRef ints() {
    return ints;
  }

  public Values type() {
    return type;
  }

  public void setType(Values type) {
    this.type = type;
    switch (type) {
    case BYTES_FIXED_DEREF:
    case BYTES_FIXED_SORTED:
    case BYTES_FIXED_STRAIGHT:
    case BYTES_VAR_DEREF:
    case BYTES_VAR_SORTED:
    case BYTES_VAR_STRAIGHT:
      bytes = new BytesRef();
      ints = null;
      floats = null;
      break;
    case PACKED_INTS:
      ints = new LongsRef(new long[1], 0, 1);
      bytes = null;
      floats = null;
      break;
    case SIMPLE_FLOAT_4BYTE:
    case SIMPLE_FLOAT_8BYTE:
      floats = new FloatsRef(new double[1], 0, 1);
      ints = null;
      bytes = null;
      break;

    }
  }

  @Override
  public void clear() {
    bytes = null;
    ints = null;
    floats = null;
    type = null;
    bytesComp = null;
  }

  @Override
  public void copyTo(AttributeImpl target) {
    ValuesAttributeImpl other = (ValuesAttributeImpl)target;
    other.setType(type);
    
    switch (type) {
    case BYTES_FIXED_DEREF:
    case BYTES_FIXED_SORTED:
    case BYTES_FIXED_STRAIGHT:
    case BYTES_VAR_DEREF:
    case BYTES_VAR_SORTED:
    case BYTES_VAR_STRAIGHT:
      other.bytes.copy(bytes);
      break;
    case PACKED_INTS:
      other.ints.copy(ints);
      break;
    case SIMPLE_FLOAT_4BYTE:
    case SIMPLE_FLOAT_8BYTE:
      other.floats.copy(floats);
      break;

    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 0;
    result = prime * result + ((bytes == null) ? 0 : bytes.hashCode());
    result = prime * result + ((floats == null) ? 0 : floats.hashCode());
    result = prime * result + ((ints == null) ? 0 : ints.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (getClass() != obj.getClass())
      return false;
    ValuesAttributeImpl other = (ValuesAttributeImpl) obj;
    if (bytes == null) {
      if (other.bytes != null)
        return false;
    } else if (!bytes.equals(other.bytes))
      return false;
    if (floats == null) {
      if (other.floats != null)
        return false;
    } else if (!floats.equals(other.floats))
      return false;
    if (ints == null) {
      if (other.ints != null)
        return false;
    } else if (!ints.equals(other.ints))
      return false;
    if (type == null) {
      if (other.type != null)
        return false;
    } else if (!type.equals(other.type))
      return false;
    return true;
  }

  public Comparator<BytesRef> bytesComparator() {
    return bytesComp;
  }

  public void setBytesComparator(Comparator<BytesRef> comp) {
    bytesComp = comp;    
  }



}
