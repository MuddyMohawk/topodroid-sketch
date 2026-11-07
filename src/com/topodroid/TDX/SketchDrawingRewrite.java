package com.topodroid.TDX;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Lossless TDR edit: parse record boundaries, patch only names/options/section coordinates.
 * Never instantiate drawing objects or reserialize symbols with today's defaults. */
final class SketchDrawingRewrite {
  private final byte[] source;
  private final ByteArrayInputStream bytes;
  private final DataInputStream in;
  private final TreeMap<Integer,Patch> patches = new TreeMap<>();
  private int version;
  private int plotType;
  private String origin;
  private int bounds = -1;
  private float minX, minY, maxX, maxY;
  private final float dx, dy;
  private final Map<String,String> names;
  private static final class Patch {
    final int end; final byte[] value;
    Patch(int end, byte[] value) { this.end = end; this.value = value; }
  }
  private SketchDrawingRewrite(byte[] source, Map<String,String> names, float dx, float dy) {
    this.source = source; this.names = names; this.dx = dx; this.dy = dy;
    bytes = new ByteArrayInputStream(source); in = new DataInputStream(bytes);
  }
  static byte[] rewrite(byte[] source, Map<String,String> names, float dx, float dy) throws IOException {
    return new SketchDrawingRewrite(source, names, dx, dy).run();
  }
  static byte[] rewrite(byte[] source, Map<String,String> names, float dx, float dy, String origin) throws IOException {
    SketchDrawingRewrite rewrite = new SketchDrawingRewrite(source,names,dx,dy);
    rewrite.origin = origin;
    return rewrite.run();
  }
  private int position() { return source.length - bytes.available(); }
  private void skip(int size) throws IOException {
    if (size < 0 || in.skipBytes(size) != size) throw new EOFException("Truncated drawing");
  }
  private String string(boolean rename) throws IOException {
    int start = position(); String value = in.readUTF();
    if (rename && names.containsKey(value)) replaceString(start, position(), names.get(value));
    return value;
  }
  private void replaceString(int start, int end, String value) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new DataOutputStream(out).writeUTF(value);
    patches.put(start, new Patch(end, out.toByteArray()));
  }
  private void number(int at, float value) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream(4);
    new DataOutputStream(out).writeFloat(value);
    patches.put(at, new Patch(at+4, out.toByteArray()));
  }
  private void coordinate(boolean shift) throws IOException {
    int at = position(); float x = in.readFloat(), y = in.readFloat();
    if (shift && (dx != 0 || dy != 0)) {
      if (Float.isNaN(x) || Float.isInfinite(x) || Float.isNaN(y) || Float.isInfinite(y)) throw new IOException("Invalid section coordinate");
      number(at, x+dx); number(at+4, y+dy);
      minX = Math.min(minX, x+dx); maxX = Math.max(maxX, x+dx);
      minY = Math.min(minY, y+dy); maxY = Math.max(maxY, y+dy);
    }
  }
  private boolean section(String name) { return "section".equals(name) || name.endsWith(":section"); }
  private void options(boolean section) throws IOException {
    int at = position(); String value = in.readUTF();
    if (!section || names.isEmpty()) return;
    Matcher matcher = Pattern.compile("(?<!\\S)(-(?:id|scrap)\\s+)(\"[^\"]*\"|\\S+)").matcher(value);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String token = matcher.group(2);
      boolean quoted = token.startsWith("\"");
      String key = quoted ? token.substring(1, token.length()-1) : token;
      String replacement = names.get(key);
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement == null ? matcher.group() :
        matcher.group(1) + (quoted ? "\""+replacement+"\"" : replacement)));
    }
    matcher.appendTail(result);
    if (!value.equals(result.toString())) replaceString(at, position(), result.toString());
  }
  private void levelScrap() throws IOException {
    if (version >= 401090) skip(4);
    if (version >= 401160) skip(4);
  }
  private void vertices(boolean shift) throws IOException {
    int count = in.readInt();
    if (count < 0 || count > bytes.available()/9) throw new IOException("Invalid drawing vertex count");
    for (int n = 0; n < count; n++) {
      coordinate(shift);
      int curve = in.readUnsignedByte();
      if (curve == 1) { coordinate(shift); coordinate(shift); }
      else if (curve != 0) throw new IOException("Invalid curve flag");
    }
  }
  private byte[] run() throws IOException {
    if (in.readUnsignedByte() != 'V') throw new IOException("Missing drawing version");
    version = in.readInt();
    if (version < 207038 || version > 604028) throw new IOException("Unsupported drawing version: " + version);
    boolean header = false, end = false;
    while (!end && bytes.available() > 0) {
      int kind = in.readUnsignedByte();
      switch (kind) {
        case 'S':
          string(true); plotType = in.readInt(); if (plotType == 8) skip(4);
          string(false); string(false); string(false); header = true; break;
        case 'I':
          bounds = position(); minX = in.readFloat(); minY = in.readFloat(); maxX = in.readFloat(); maxY = in.readFloat();
          int north = in.readInt(); if (north == 1) skip(16); else if (north != 0) throw new IOException("Invalid bounds"); break;
        case 'N': skip(4); break;
        case 'P': {
          int coordinates = position(); skip(8); String symbol = string(false);
          if (version >= 401147) string(false);
          skip(8); levelScrap(); if (version >= 303066) string(false);
          options(section(symbol));
          if (section(symbol) && (dx != 0 || dy != 0)) {
            DataInputStream coords = new DataInputStream(new ByteArrayInputStream(source, coordinates, 8));
            float x = coords.readFloat()+dx, y = coords.readFloat()+dy;
            if (Float.isNaN(x) || Float.isInfinite(x) || Float.isNaN(y) || Float.isInfinite(y)) throw new IOException("Invalid section coordinate");
            number(coordinates,x); number(coordinates+4,y);
            minX = Math.min(minX,x); minY = Math.min(minY,y); maxX = Math.max(maxX,x); maxY = Math.max(maxY,y);
          }
          break;
        }
        case 'L': {
          String symbol = string(false); if (version >= 401147) string(false);
          skip(6); if (version >= 602055) skip(4); levelScrap();
          options(section(symbol)); vertices(section(symbol)); break;
        }
        case 'A':
          string(false); if (version >= 401147) string(false); string(false);
          skip(9); levelScrap(); if (version >= 604028) string(false); vertices(false); break;
        case 'T': case 'Y': case 'Z':
          skip(8); if (version > 207043) skip(4); skip(4);
          if (kind == 'T') { if (version > 401090) skip(4); if (version > 401160) skip(4); }
          else { if (version >= 401090) skip(4); if (kind == 'Y' ? version >= 401160 : version > 401160) skip(4); }
          string(false); string(false);
          if (kind != 'T') skip(4);
          if (kind == 'Y' && version >= 602067) {
            string(false); int picture = in.readInt();
            if (picture == 1) skip(12); else if (picture != 0) throw new IOException("Invalid picture flag");
          }
          break;
        case 'J': skip(12); levelScrap(); break;
        case 'U': skip(12); levelScrap(); string(false); break;
        case 'X':
          skip(8); string(false); levelScrap(); if (in.readInt() != -1) skip(8); break;
        case 'F': break;
        case 'D': {
          skip(20);
          int at=position(); string(false);
          if (origin!=null) replaceString(at,position(),origin);
          string(false); string(plotType!=1 && plotType!=2 && plotType!=8); string(false);
          break;
        }
        case 'E': end = true; break;
        default: throw new IOException("Unsupported drawing record: " + kind);
      }
    }
    if (!header || !end || bytes.available() != 0) throw new IOException("Incomplete drawing");
    if (bounds >= 0 && (dx != 0 || dy != 0)) {
      number(bounds,minX); number(bounds+4,minY); number(bounds+8,maxX); number(bounds+12,maxY);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream(source.length);
    int offset = 0;
    for (Map.Entry<Integer,Patch> entry : patches.entrySet()) {
      out.write(source,offset,entry.getKey()-offset); out.write(entry.getValue().value);
      offset = entry.getValue().end;
    }
    out.write(source,offset,source.length-offset);
    return out.toByteArray();
  }
}
