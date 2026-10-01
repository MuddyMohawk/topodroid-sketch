package com.topodroid.TDX;

import com.topodroid.types.PlotType;
import com.topodroid.util.TDLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned section metadata in configs; ZIPs use plot names rather than local IDs. */
final class SectionReferenceRepository
{
  static final String PREFIX = "section_refs:";
  static final String ARCHIVE_ENTRY = "section_references.json";
  private SectionReferenceRepository() { }

  static String surveyPrefix( long sid ) { return PREFIX + sid + ":"; }
  static String key( long sid, long pid ) { return surveyPrefix( sid ) + pid; }

  static SectionReferenceSelection load( DataHelper data, long sid, PlotInfo plot, List<DBlock> shots )
  {
    String value = data.getValue( key( sid, plot.id ) );
    if ( value != null ) {
      try { return decode( new JSONObject( value ) ); }
      catch ( JSONException e ) { TDLog.e( "Invalid section reference choices: " + e.getMessage() ); }
    }
    return defaults( plot, shots );
  }

  static SectionReferenceSelection defaults( PlotInfo plot, List<DBlock> shots )
  {
    SectionReferenceSelection selection = new SectionReferenceSelection();
    if ( PlotType.isMultilegSection( plot.type, plot.view ) ) {
      Set<Long> ids = new java.util.HashSet<>();
      if ( plot.start != null ) for ( String id : plot.start.trim().split( "\\s+" ) ) {
        try { ids.add( Long.parseLong( id ) ); } catch ( NumberFormatException e ) { /* Missing defining leg. */ }
      }
      for ( DBlock shot : shots ) if ( shot.isLeg() && ids.contains( shot.mId ) ) {
        selection.stations.add( shot.mFrom );
        selection.stations.add( shot.mTo );
      }
    } else {
      if ( plot.start != null && ! plot.start.isEmpty() ) selection.stations.add( plot.start );
      if ( PlotType.isLegSection( plot.type ) && plot.view != null && ! plot.view.isEmpty() ) selection.stations.add( plot.view );
    }
    return selection;
  }

  static boolean save( DataHelper data, long sid, long pid, SectionReferenceSelection selection )
  {
    try { return data.saveSectionReferenceValue( sid, pid, encode( selection ).toString() ); }
    catch ( JSONException e ) { TDLog.e( "Cannot encode section choices: " + e.getMessage() ); return false; }
  }

  static JSONObject encode( SectionReferenceSelection selection ) throws JSONException
  {
    JSONObject json = new JSONObject();
    json.put( "version", 1 );
    json.put( "stations", new JSONArray( selection.stations ) );
    json.put( "hiddenSplays", new JSONArray( selection.hiddenSplays ) );
    json.put( "showOutOfPlane", selection.showOutOfPlane );
    return json;
  }

  static SectionReferenceSelection decode( JSONObject json ) throws JSONException
  {
    if ( json.getInt( "version" ) != 1 ) throw new JSONException( "Unsupported section reference version" );
    SectionReferenceSelection selection = new SectionReferenceSelection();
    readNames( json.getJSONArray( "stations" ), selection.stations );
    readNames( json.getJSONArray( "hiddenSplays" ), selection.hiddenSplays );
    selection.showOutOfPlane = json.getBoolean( "showOutOfPlane" );
    return selection;
  }

  private static void readNames( JSONArray names, Set<String> target ) throws JSONException
  {
    for ( int i = 0; i < names.length(); ++i ) {
      Object value = names.get( i );
      if ( ! ( value instanceof String ) || ((String)value).isEmpty() ) throw new JSONException( "Invalid station name" );
      target.add( (String)value );
    }
  }

  static String exportArchive( DataHelper data, long sid ) throws JSONException
  {
    JSONObject sections = new JSONObject();
    for ( PlotInfo plot : data.selectAllPlots( sid ) ) if ( PlotType.isAnySection( plot.type ) ) {
      String value = data.getValue( key( sid, plot.id ) );
      if ( value != null ) sections.put( plot.name, encode( decode( new JSONObject( value ) ) ) );
    }
    if ( sections.length() == 0 ) return null;
    JSONObject archive = new JSONObject();
    archive.put( "version", 1 );
    archive.put( "sections", sections );
    return archive.toString();
  }

  static boolean importArchive( DataHelper data, long sid, String text )
  {
    if ( text == null ) return true;
    try {
      JSONObject archive = new JSONObject( text );
      if ( archive.getInt( "version" ) != 1 ) throw new JSONException( "Unsupported section archive version" );
      JSONObject sections = archive.getJSONObject( "sections" );
      Map<Long,String> values = new LinkedHashMap<>();
      java.util.Iterator<String> names = sections.keys();
      while ( names.hasNext() ) {
        String name = names.next();
        SectionReferenceSelection selection = decode( sections.getJSONObject( name ) );
        PlotInfo plot = data.getPlotInfo( sid, name );
        if ( plot != null && PlotType.isAnySection( plot.type ) ) values.put( plot.id, encode( selection ).toString() );
      }
      return data.saveSectionReferenceValues( sid, values );
    } catch ( JSONException e ) {
      TDLog.e( "Cannot import section reference choices: " + e.getMessage() );
      return false;
    }
  }
}
