/* @file SymbolEditorDocument.java
 *
 * @author MuddyMohawk
 * @date jun 2026
 *
 * @brief TopoDroid in-app symbol file editor support
 * --------------------------------------------------------
 *  Copyright This software is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.TDX;

import com.topodroid.types.SymbolType;
import com.topodroid.util.TDFile;
import com.topodroid.util.TDLocale;
import com.topodroid.util.TDLog;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Locale;

class SymbolEditorDocument
{
  static class Field
  {
    final int mLineIndex;
    final String mKey;
    final String mValue;

    Field( int lineIndex, String key, String value )
    {
      mLineIndex = lineIndex;
      mKey = key;
      mValue = value;
    }

    boolean isColor()
    {
      return "color".equals( mKey );
    }

    int colorValue()
    {
      String token = firstToken( mValue );
      if ( token == null ) return 0xffffffff;
      try {
        return 0xff000000 | ( Integer.decode( token ).intValue() & 0x00ffffff );
      } catch ( NumberFormatException e ) {
        return 0xffffffff;
      }
    }

    String colorText( int color )
    {
      String value = mValue.trim();
      String rest = "";
      int len = value.length();
      int pos = 0;
      while ( pos < len && ! Character.isWhitespace( value.charAt( pos ) ) ) ++pos;
      while ( pos < len && Character.isWhitespace( value.charAt( pos ) ) ) ++pos;
      if ( pos < len ) rest = value.substring( pos );

      String colorText = String.format( Locale.US, "0x%06x", color & 0x00ffffff );
      return ( rest.length() == 0 ) ? colorText : colorText + " " + rest;
    }
  }

  private static final String BACKUP_DIR = "symbol-edit-backup";

  private final int mType;
  private final SymbolInterface mSymbol;
  private final File mFile;
  private final String mOriginalThName;
  private final String mEncoding;

  private SymbolEditorDocument( int type, SymbolInterface symbol, File file, String encoding )
  {
    mType = type;
    mSymbol = symbol;
    mFile = file;
    mOriginalThName = Symbol.deprefix_u( symbol.getThName() );
    mEncoding = encoding;
  }

  static SymbolEditorDocument create( EnableSymbol symbol )
  {
    if ( symbol == null ) return null;
    SymbolInterface item = symbol.getSymbol();
    if ( item == null ) return null;
    File file = getSymbolFile( symbol.getType(), item );
    if ( file == null || ! file.exists() || ! file.isFile() ) return null;
    return new SymbolEditorDocument( symbol.getType(), item, file, detectEncoding( file ) );
  }

  static boolean isEditable( EnableSymbol symbol )
  {
    if ( symbol == null || symbol.getSymbol() == null ) return false;
    if ( isHardCoded( symbol.getType(), symbol.getSymbol() ) ) return false;
    File file = getSymbolFile( symbol.getType(), symbol.getSymbol() );
    return file != null && file.exists() && file.isFile();
  }

  static boolean isHardCoded( int type, SymbolInterface symbol )
  {
    if ( symbol == null ) return true;
    String thName = Symbol.deprefix_u( symbol.getThName() );
    if ( thName == null ) return true;
    switch ( type ) {
      case SymbolType.POINT:
        return thName.equals( Symbol.deprefix_u( SymbolLibrary.USER ) )
            || thName.equals( SymbolLibrary.LABEL )
            || thName.equals( SymbolLibrary.SECTION )
            || thName.equals( SymbolLibrary.PICTURE )
            || thName.equals( SymbolLibrary.REFERENCE );
      case SymbolType.LINE:
        return thName.equals( Symbol.deprefix_u( SymbolLibrary.USER ) )
            || thName.equals( SymbolLibrary.WALL )
            || thName.equals( SymbolLibrary.SECTION );
      case SymbolType.AREA:
        return thName.equals( Symbol.deprefix_u( SymbolLibrary.USER ) );
    }
    return true;
  }

  static String[] libraryFullNames( int type )
  {
    int size = 0;
    switch ( type ) {
      case SymbolType.POINT: size = BrushManager.getPointLibSize(); break;
      case SymbolType.LINE:  size = BrushManager.getLineLibSize();  break;
      case SymbolType.AREA:  size = BrushManager.getAreaLibSize();  break;
      default: return null;
    }
    String[] names = new String[ size ];
    for ( int k = 0; k < size; ++k ) {
      switch ( type ) {
        case SymbolType.POINT: names[k] = BrushManager.getPointFullThName( k ); break;
        case SymbolType.LINE:  names[k] = BrushManager.getLineFullThName( k );  break;
        case SymbolType.AREA:  names[k] = BrushManager.getAreaFullThName( k );  break;
      }
    }
    return names;
  }

  static int[] indexMap( int type, String[] oldNames )
  {
    if ( oldNames == null ) return null;
    int[] map = new int[ oldNames.length ];
    for ( int k = 0; k < oldNames.length; ++k ) map[k] = getIndexByThName( type, oldNames[k] );
    return map;
  }

  private static int getIndexByThName( int type, String thName )
  {
    switch ( type ) {
      case SymbolType.POINT: return BrushManager.getPointIndexByThName( thName );
      case SymbolType.LINE:  return BrushManager.getLineIndexByThName( thName );
      case SymbolType.AREA:  return BrushManager.getAreaIndexByThName( thName );
    }
    return -1;
  }

  private static File getSymbolFile( int type, SymbolInterface symbol )
  {
    String filename = Symbol.deprefix_u( symbol.getThName() );
    if ( filename == null || filename.length() == 0 ) return null;
    switch ( type ) {
      case SymbolType.POINT: return TDPath.getPointFile( filename );
      case SymbolType.LINE:  return TDPath.getLineFile( filename );
      case SymbolType.AREA:  return TDPath.getAreaFile( filename );
    }
    return null;
  }

  File file()
  {
    return mFile;
  }

  String symbolName()
  {
    return mSymbol.getName();
  }

  String readText() throws IOException
  {
    return readFileText( mFile, mEncoding );
  }

  String saveText( String text )
  {
    String error = validateIdentity( text );
    if ( error != null ) return error;

    File tmp = null;
    try {
      File backupDir = backupDir();
      if ( ! backupDir.exists() && ! backupDir.mkdirs() ) return "Cannot create backup folder";

      tmp = new File( backupDir, mFile.getName() + ".tmp" );
      writeFileText( tmp, text, "UTF-8" );
      Symbol parsed = parseSymbolFile( tmp );
      if ( parsed == null || parsed.getName() == null || parsed.isThName( null ) ) return "Symbol file did not parse";
      if ( ! mOriginalThName.equals( Symbol.deprefix_u( parsed.getThName() ) ) ) return "Symbol name changed";

      backupOriginal( backupDir );
      writeFileText( mFile, text, "UTF-8" );
    } catch ( IOException e ) {
      TDLog.e( "symbol editor save error: " + e.getMessage() );
      return "Save failed: " + e.getMessage();
    } finally {
      if ( tmp != null && tmp.exists() && ! tmp.delete() ) TDLog.e( "symbol editor temp delete failed" );
    }
    return null;
  }

  private String validateIdentity( String text )
  {
    String kind = null;
    String thName = null;
    String[] lines = splitLines( text );
    boolean inBlock = false;
    for ( String line : lines ) {
      ParsedLine parsed = parseLine( line );
      if ( parsed == null ) continue;
      String key = parsed.mKey;
      if ( isBlockStart( key ) ) {
        inBlock = true;
      } else if ( isBlockEnd( key ) ) {
        inBlock = false;
      }
      if ( inBlock ) continue;
      if ( "symbol".equals( key ) ) {
        kind = firstToken( parsed.mValue );
      } else if ( "th_name".equals( key ) ) {
        thName = firstToken( parsed.mValue );
      }
    }
    if ( ! kindForType( mType ).equals( kind ) ) return "Symbol type must stay " + kindForType( mType );
    if ( thName == null || thName.length() == 0 ) return "Symbol th_name is missing";
    if ( ! mOriginalThName.equals( Symbol.deprefix_u( thName ) ) ) return "Symbol th_name cannot be changed";
    return null;
  }

  ArrayList< Field > fields( String text )
  {
    ArrayList< Field > fields = new ArrayList<>();
    String[] lines = splitLines( text );
    boolean inBlock = false;
    for ( int k = 0; k < lines.length; ++k ) {
      ParsedLine parsed = parseLine( lines[k] );
      if ( parsed == null ) continue;
      String key = parsed.mKey;
      if ( isBlockEnd( key ) ) {
        inBlock = false;
        continue;
      }
      if ( inBlock ) continue;
      if ( isBlockStart( key ) ) {
        inBlock = true;
        continue;
      }
      if ( isEditableField( key ) ) fields.add( new Field( k, key, parsed.mValue ) );
    }
    return fields;
  }

  static String updateFieldValue( String text, Field field, String value )
  {
    if ( text == null || field == null ) return text;
    String[] lines = splitLines( text );
    if ( field.mLineIndex < 0 || field.mLineIndex >= lines.length ) return text;

    ParsedLine parsed = parseLine( lines[ field.mLineIndex ] );
    if ( parsed == null || ! field.mKey.equals( parsed.mKey ) ) return text;

    lines[ field.mLineIndex ] = parsed.lineWithValue( normalizeValue( parsed.mKey, value ) );
    return joinLines( lines );
  }

  private Symbol parseSymbolFile( File file )
  {
    String locale = "name-" + TDLocale.getLocaleCode();
    String iso = "UTF-8";
    switch ( mType ) {
      case SymbolType.POINT: return new SymbolPoint( file.getPath(), mFile.getName(), locale, iso );
      case SymbolType.LINE:  return new SymbolLine( file.getPath(), mFile.getName(), locale, iso );
      case SymbolType.AREA:  return new SymbolArea( file.getPath(), mFile.getName(), locale, iso );
    }
    return null;
  }

  private void backupOriginal( File backupDir ) throws IOException
  {
    File backup = new File( backupDir, mFile.getName() + "." + System.currentTimeMillis() + ".bak" );
    FileInputStream in = null;
    FileOutputStream out = null;
    try {
      in = new FileInputStream( mFile );
      out = new FileOutputStream( backup );
      byte[] buffer = new byte[8192];
      int n;
      while ( ( n = in.read( buffer ) ) >= 0 ) out.write( buffer, 0, n );
    } finally {
      if ( in != null ) in.close();
      if ( out != null ) out.close();
    }
  }

  private File backupDir()
  {
    File root = TDFile.getPrivateDir( BACKUP_DIR );
    return new File( root, kindForType( mType ) );
  }

  private static boolean isEditableField( String key )
  {
    return "group".equals( key )
        || "options".equals( key )
        || "color".equals( key )
        || "alpha".equals( key )
        || "width".equals( key )
        || "level".equals( key )
        || "roundtrip".equals( key )
        || "orientation".equals( key )
        || "declination".equals( key )
        || "orientable".equals( key )
        || "closed".equals( key )
        || "close-horizontal".equals( key )
        || "line_pattern".equals( key )
        || "has_text".equals( key )
        || "has_value".equals( key )
        || "style".equals( key );
  }

  private static boolean isBlockStart( String key )
  {
    return "path".equals( key ) || "effect".equals( key ) || "bitmap".equals( key );
  }

  private static boolean isBlockEnd( String key )
  {
    return "endpath".equals( key ) || "endeffect".equals( key ) || "endbitmap".equals( key );
  }

  private static String normalizeValue( String key, String value )
  {
    if ( value == null ) return "";
    if ( "name".equals( key ) || key.startsWith( "name-" ) ) return value.trim().replace( ' ', '_' );
    return value.trim();
  }

  private static String detectEncoding( File file )
  {
    FileInputStream in = null;
    try {
      in = new FileInputStream( file );
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int n;
      while ( ( n = in.read( buffer ) ) >= 0 ) out.write( buffer, 0, n );
      byte[] data = out.toByteArray();
      CharsetDecoder decoder = Charset.forName( "UTF-8" ).newDecoder();
      decoder.onMalformedInput( CodingErrorAction.REPORT );
      decoder.onUnmappableCharacter( CodingErrorAction.REPORT );
      decoder.decode( ByteBuffer.wrap( data ) );
      return "UTF-8";
    } catch ( Exception e ) {
      return "ISO-8859-1";
    } finally {
      if ( in != null ) {
        try {
          in.close();
        } catch ( IOException e ) {
          TDLog.e( "symbol editor close error: " + e.getMessage() );
        }
      }
    }
  }

  private static String kindForType( int type )
  {
    switch ( type ) {
      case SymbolType.POINT: return "point";
      case SymbolType.LINE:  return "line";
      case SymbolType.AREA:  return "area";
    }
    return "";
  }

  private static String readFileText( File file, String encoding ) throws IOException
  {
    StringBuilder text = new StringBuilder();
    BufferedReader br = null;
    try {
      br = new BufferedReader( new InputStreamReader( new FileInputStream( file ), encoding ) );
      String line;
      boolean first = true;
      while ( ( line = br.readLine() ) != null ) {
        if ( first ) {
          first = false;
        } else {
          text.append( '\n' );
        }
        text.append( line );
      }
    } finally {
      if ( br != null ) br.close();
    }
    return text.toString();
  }

  private static void writeFileText( File file, String text, String encoding ) throws IOException
  {
    File parent = file.getParentFile();
    if ( parent != null && ! parent.exists() && ! parent.mkdirs() ) throw new IOException( "Cannot create folder" );
    OutputStreamWriter writer = null;
    try {
      writer = new OutputStreamWriter( new FileOutputStream( file ), encoding );
      writer.write( text );
      writer.write( '\n' );
    } finally {
      if ( writer != null ) writer.close();
    }
  }

  private static String[] splitLines( String text )
  {
    if ( text == null ) return new String[] { "" };
    return text.replace( "\r\n", "\n" ).replace( '\r', '\n' ).split( "\n", -1 );
  }

  private static String joinLines( String[] lines )
  {
    StringBuilder sb = new StringBuilder();
    for ( int k = 0; k < lines.length; ++k ) {
      if ( k > 0 ) sb.append( '\n' );
      sb.append( lines[k] );
    }
    return sb.toString();
  }

  private static String firstToken( String value )
  {
    if ( value == null ) return null;
    String trim = value.trim();
    if ( trim.length() == 0 ) return null;
    int pos = 0;
    while ( pos < trim.length() && ! Character.isWhitespace( trim.charAt( pos ) ) ) ++pos;
    return trim.substring( 0, pos );
  }

  private static ParsedLine parseLine( String raw )
  {
    if ( raw == null ) return null;
    int hash = raw.indexOf( '#' );
    String body = ( hash >= 0 ) ? raw.substring( 0, hash ) : raw;
    String comment = ( hash >= 0 ) ? raw.substring( hash ) : "";

    int len = body.length();
    int keyStart = 0;
    while ( keyStart < len && Character.isWhitespace( body.charAt( keyStart ) ) ) ++keyStart;
    if ( keyStart >= len ) return null;

    int keyEnd = keyStart;
    while ( keyEnd < len && ! Character.isWhitespace( body.charAt( keyEnd ) ) ) ++keyEnd;
    String key = body.substring( keyStart, keyEnd );

    int valueStart = keyEnd;
    while ( valueStart < len && Character.isWhitespace( body.charAt( valueStart ) ) ) ++valueStart;
    String value = ( valueStart < len ) ? body.substring( valueStart ).trim() : "";

    return new ParsedLine( raw.substring( 0, keyStart ), key, comment, value );
  }

  private static class ParsedLine
  {
    final String mIndent;
    final String mKey;
    final String mComment;
    final String mValue;

    ParsedLine( String indent, String key, String comment, String value )
    {
      mIndent = indent;
      mKey = key;
      mComment = comment;
      mValue = value;
    }

    String lineWithValue( String value )
    {
      StringBuilder sb = new StringBuilder();
      sb.append( mIndent ).append( mKey );
      if ( value != null && value.length() > 0 ) sb.append( ' ' ).append( value );
      if ( mComment.length() > 0 ) sb.append( ' ' ).append( mComment );
      return sb.toString();
    }
  }
}
