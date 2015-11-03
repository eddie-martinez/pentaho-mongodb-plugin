/*!
 * Copyright 2010 - 2013 Pentaho Corporation.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.pentaho.di.trans.steps.mongodboutput;

import com.google.common.collect.ImmutableMap;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DBObject;
import com.mongodb.MongoException;
import com.mongodb.WriteResult;
import junit.framework.Assert;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettlePluginException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.plugins.PluginRegistry;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaFactory;
import org.pentaho.di.core.row.value.ValueMetaPluginType;
import org.pentaho.di.core.row.value.ValueMetaString;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.variables.Variables;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.steps.mongodbinput.BaseMongoDbStepTest;
import org.pentaho.di.trans.steps.mongodboutput.MongoDbOutputData.MongoTopLevel;
import org.pentaho.metastore.api.IMetaStore;
import org.pentaho.mongo.MongoDbException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.*;
import static org.pentaho.di.trans.steps.mongodboutput.MongoDbOutputData.kettleRowToMongo;

/**
 * Unit tests for MongoDbOutput
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 */
public class MongoDbOutputTest extends BaseMongoDbStepTest {

  private static final Class<?> PKG = MongoDbOutputMeta.class;
  @Mock private MongoDbOutputData stepDataInterace;
  @Mock private MongoDbOutputMeta stepMetaInterface;

  private MongoDbOutput dbOutput;
  RowMeta rowMeta = new RowMeta();
  Object[] rowData;

  private List<MongoDbOutputMeta.MongoField> mongoFields = new ArrayList<MongoDbOutputMeta.MongoField>();

  @Before public void before() throws MongoDbException {
    super.before();

    when( stepDataInterace.getConnection() ).thenReturn( mongoClientWrapper );
    when( stepMetaInterface.getPort() ).thenReturn( "1010" );
    when( stepMetaInterface.getHostnames() ).thenReturn( "host" );

    dbOutput = new MongoDbOutput( stepMeta, stepDataInterace, 1, transMeta, trans ) {
      public Object[] getRow() {
        return rowData;
      }

      public RowMetaInterface getInputRowMeta() {
        return rowMeta;
      }
    };
  }

  @BeforeClass public static void beforeClass() throws KettlePluginException {
    PluginRegistry.addPluginType( ValueMetaPluginType.getInstance() );
    PluginRegistry.init();
  }

  @Test public void testCheckTopLevelConsistencyPathsAreConsistentRecord() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    MongoTopLevel topLevel = MongoDbOutputData.checkTopLevelConsistency( paths, new Variables() );
    assertTrue( topLevel == MongoTopLevel.RECORD );
  }

  @Test public void testCheckTopLevelConsistencyPathsAreConsistentArray() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "[0]";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "[0]";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    MongoTopLevel topLevel = MongoDbOutputData.checkTopLevelConsistency( paths, new Variables() );
    assertTrue( topLevel == MongoTopLevel.ARRAY );
  }

  @Test public void testCheckTopLevelConsistencyPathsAreInconsistent() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "[0]";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    MongoTopLevel topLevel = MongoDbOutputData.checkTopLevelConsistency( paths, new Variables() );

    assertTrue( topLevel == MongoTopLevel.INCONSISTENT );
  }

  @Test( expected = KettleException.class ) public void testCheckTopLevelConsistencyNoFieldsDefined()
    throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputData.checkTopLevelConsistency( paths, new Variables() );
  }

  /**
   * PDI-11045. When doing a non-modifier update/upsert it should not be necessary to define query fields a second time
   * in the step (i.e. those paths that the user defines for the matching conditions should be placed into the update
   * document automatically).
   *
   * @throws KettleException
   */
  @Test public void testUpdateObjectContainsQueryFields() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_updateMatchField = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    RowMetaInterface rmi = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_INTEGER );
    rmi.addValueMeta( vm );

    Object[] row = new Object[2];
    row[0] = "value1";
    row[1] = new Long( 12 );
    VariableSpace vs = new Variables();

    for ( MongoDbOutputMeta.MongoField f : paths ) {
      f.init( vs );
    }

    DBObject result = kettleRowToMongo( paths, rmi, row, vs, MongoDbOutputData.MongoTopLevel.RECORD, false );

    assertEquals( result.toString(), "{ \"field1\" : \"value1\" , \"field2\" : 12}" );
  }

  /**
   * PDI-11045. Here we test backwards compatibility for old ktrs that were developed before 11045. In these ktrs query
   * paths had to be specified a second time in the step in order to get them into the update/upsert object. Now we
   * assume that there will never be a situation where the user might not want the match fields present in the update
   * object
   *
   * @throws KettleException
   */
  @Test public void testUpdateObjectBackwardsCompatibility() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_updateMatchField = true;
    paths.add( mf );

    // same as previous field (but not a match condition). Prior to PDI-11045 the
    // user had to specify the match conditions a second time (but not marked in the
    // step as a match condition) in order to get them into the update/upsert object
    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_updateMatchField = false;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    RowMetaInterface rmi = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_INTEGER );
    rmi.addValueMeta( vm );

    Object[] row = new Object[2];
    row[0] = "value1";
    row[1] = new Long( 12 );
    VariableSpace vs = new Variables();

    for ( MongoDbOutputMeta.MongoField f : paths ) {
      f.init( vs );
    }

    DBObject result = kettleRowToMongo( paths, rmi, row, vs, MongoDbOutputData.MongoTopLevel.RECORD, false );

    // here we expect that field1 does *not* occur twice in the update object
    assertEquals( result.toString(), "{ \"field1\" : \"value1\" , \"field2\" : 12}" );
  }

  @Test public void testTopLevelObjectStructureNoNestedDocs() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    RowMetaInterface rmi = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_INTEGER );
    rmi.addValueMeta( vm );

    Object[] row = new Object[2];
    row[0] = "value1";
    row[1] = new Long( 12 );
    VariableSpace vs = new Variables();

    for ( MongoDbOutputMeta.MongoField f : paths ) {
      f.init( vs );
    }

    DBObject result = kettleRowToMongo( paths, rmi, row, vs, MongoDbOutputData.MongoTopLevel.RECORD, false );

    assertEquals( result.toString(), "{ \"field1\" : \"value1\" , \"field2\" : 12}" );

  }

  @Test public void testTopLevelArrayStructureWithPrimitives() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "[0]";
    mf.m_useIncomingFieldNameAsMongoFieldName = false;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "[1]";
    mf.m_useIncomingFieldNameAsMongoFieldName = false;
    paths.add( mf );

    RowMetaInterface rmi = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_INTEGER );
    rmi.addValueMeta( vm );

    Object[] row = new Object[2];
    row[0] = "value1";
    row[1] = new Long( 12 );
    VariableSpace vs = new Variables();

    for ( MongoDbOutputMeta.MongoField f : paths ) {
      f.init( vs );
    }

    DBObject result = kettleRowToMongo( paths, rmi, row, vs, MongoDbOutputData.MongoTopLevel.ARRAY, false );

    assertEquals( result.toString(), "[ \"value1\" , 12]" );
  }

  @Test public void testTopLevelArrayStructureWithObjects() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "[0]";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "[1]";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    RowMetaInterface rmi = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_INTEGER );
    rmi.addValueMeta( vm );

    Object[] row = new Object[2];
    row[0] = "value1";
    row[1] = new Long( 12 );
    VariableSpace vs = new Variables();

    for ( MongoDbOutputMeta.MongoField f : paths ) {
      f.init( vs );
    }

    DBObject result = kettleRowToMongo( paths, rmi, row, vs, MongoDbOutputData.MongoTopLevel.ARRAY, false );

    assertEquals( result.toString(), "[ { \"field1\" : \"value1\"} , { \"field2\" : 12}]" );
  }

  @Test public void testTopLevelArrayStructureContainingOneObjectMutipleFields() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "[0]";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "[0]";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    RowMetaInterface rmi = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_INTEGER );
    rmi.addValueMeta( vm );

    Object[] row = new Object[2];
    row[0] = "value1";
    row[1] = new Long( 12 );
    VariableSpace vs = new Variables();

    for ( MongoDbOutputMeta.MongoField f : paths ) {
      f.init( vs );
    }

    DBObject result = kettleRowToMongo( paths, rmi, row, vs, MongoDbOutputData.MongoTopLevel.ARRAY, false );

    assertEquals( result.toString(), "[ { \"field1\" : \"value1\" , \"field2\" : 12}]" );
  }

  @Test public void testTopLevelArrayStructureContainingObjectWithArray() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "[0].inner[0]";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "[0].inner[1]";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    RowMetaInterface rmi = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_INTEGER );
    rmi.addValueMeta( vm );

    Object[] row = new Object[2];
    row[0] = "value1";
    row[1] = new Long( 12 );
    VariableSpace vs = new Variables();

    for ( MongoDbOutputMeta.MongoField f : paths ) {
      f.init( vs );
    }

    DBObject result = kettleRowToMongo( paths, rmi, row, vs, MongoDbOutputData.MongoTopLevel.ARRAY, false );

    assertEquals( result.toString(), "[ { \"inner\" : [ { \"field1\" : \"value1\"} , { \"field2\" : 12}]}]" );
  }

  @Test public void testTopLevelObjectStructureOneLevelNestedDoc() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "nestedDoc";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    RowMetaInterface rmi = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_INTEGER );
    rmi.addValueMeta( vm );

    Object[] row = new Object[2];
    row[0] = "value1";
    row[1] = new Long( 12 );
    VariableSpace vs = new Variables();

    for ( MongoDbOutputMeta.MongoField f : paths ) {
      f.init( vs );
    }

    DBObject result = kettleRowToMongo( paths, rmi, row, vs, MongoDbOutputData.MongoTopLevel.RECORD, false );

    assertEquals( result.toString(), "{ \"field1\" : \"value1\" , \"nestedDoc\" : { \"field2\" : 12}}" );
  }

  @Test public void testTopLevelObjectStructureTwoLevelNested() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "nestedDoc.secondNested";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "nestedDoc";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    RowMetaInterface rmi = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_INTEGER );
    rmi.addValueMeta( vm );

    Object[] row = new Object[2];
    row[0] = "value1";
    row[1] = new Long( 12 );
    VariableSpace vs = new Variables();

    for ( MongoDbOutputMeta.MongoField f : paths ) {
      f.init( vs );
    }

    DBObject result = kettleRowToMongo( paths, rmi, row, vs, MongoDbOutputData.MongoTopLevel.RECORD, false );

    assertEquals( result.toString(),
        "{ \"nestedDoc\" : { \"secondNested\" : { \"field1\" : \"value1\"} , \"field2\" : 12}}" );
  }

  @Test public void testModifierUpdateWithMultipleModifiersOfSameType() throws KettleException, MongoDbException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();
    MongoDbOutputData data = new MongoDbOutputData();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_modifierUpdateOperation = "$set";
    mf.m_modifierOperationApplyPolicy = "Insert&Update";
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "nestedDoc";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_modifierUpdateOperation = "$set";
    mf.m_modifierOperationApplyPolicy = "Insert&Update";
    paths.add( mf );

    RowMetaInterface rm = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rm.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_STRING );
    rm.addValueMeta( vm );

    VariableSpace vars = new Variables();
    Object[] dummyRow = new Object[] { "value1", "value2" };

    // test to see that having more than one path specify the $set operation
    // results
    // in an update object with two entries
    DBObject
        modifierUpdate =
        data.getModifierUpdateObject( paths, rm, dummyRow, vars, MongoDbOutputData.MongoTopLevel.RECORD );

    assertTrue( modifierUpdate != null );
    assertTrue( modifierUpdate.get( "$set" ) != null );
    DBObject setOpp = (DBObject) modifierUpdate.get( "$set" );
    assertEquals( setOpp.keySet().size(), 2 );
  }

  @Test public void testModifierSetComplexArrayGrouping() throws KettleException, MongoDbException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();
    MongoDbOutputData data = new MongoDbOutputData();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "bob.fred[0].george";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_modifierUpdateOperation = "$set";
    mf.m_modifierOperationApplyPolicy = "Insert&Update";
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "bob.fred[0].george";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_modifierUpdateOperation = "$set";
    mf.m_modifierOperationApplyPolicy = "Insert&Update";
    paths.add( mf );

    RowMetaInterface rm = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rm.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_STRING );
    rm.addValueMeta( vm );

    VariableSpace vars = new Variables();
    Object[] dummyRow = new Object[] { "value1", "value2" };

    DBObject
        modifierUpdate =
        data.getModifierUpdateObject( paths, rm, dummyRow, vars, MongoDbOutputData.MongoTopLevel.RECORD );

    assertTrue( modifierUpdate != null );
    assertTrue( modifierUpdate.get( "$set" ) != null );
    DBObject setOpp = (DBObject) modifierUpdate.get( "$set" );

    // in this case, we have the same path up to the array (bob.fred). The
    // remaining
    // terminal fields should be grouped into one record "george" as the first
    // entry in
    // the array - so there should be one entry for $set
    assertEquals( setOpp.keySet().size(), 1 );

    // check the resulting update structure
    assertEquals( modifierUpdate.toString(),
        "{ \"$set\" : { \"bob.fred\" : [ { \"george\" : { \"field1\" : \"value1\" , \"field2\" : \"value2\"}}]}}" );
  }

  @Test public void testModifierPushComplexObject() throws KettleException, MongoDbException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();
    MongoDbOutputData data = new MongoDbOutputData();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "bob.fred[].george";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_modifierUpdateOperation = "$push";
    mf.m_modifierOperationApplyPolicy = "Insert&Update";
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "bob.fred[].george";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_modifierUpdateOperation = "$push";
    mf.m_modifierOperationApplyPolicy = "Insert&Update";
    paths.add( mf );

    RowMetaInterface rm = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rm.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_STRING );
    rm.addValueMeta( vm );

    VariableSpace vars = new Variables();
    Object[] dummyRow = new Object[] { "value1", "value2" };

    DBObject
        modifierUpdate =
        data.getModifierUpdateObject( paths, rm, dummyRow, vars, MongoDbOutputData.MongoTopLevel.RECORD );

    assertTrue( modifierUpdate != null );
    assertTrue( modifierUpdate.get( "$push" ) != null );
    DBObject setOpp = (DBObject) modifierUpdate.get( "$push" );

    // in this case, we have the same path up to the array (bob.fred). The
    // remaining
    // terminal fields should be grouped into one record "george" for $push to
    // append
    // to the end of the array
    assertEquals( setOpp.keySet().size(), 1 );

    assertEquals( modifierUpdate.toString(),
        "{ \"$push\" : { \"bob.fred\" : { \"george\" : { \"field1\" : \"value1\" , \"field2\" : \"value2\"}}}}" );
  }

  @Test public void testModifierPushComplexObjectWithJsonNestedDoc() throws KettleException, MongoDbException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();
    MongoDbOutputData data = new MongoDbOutputData();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "bob.fred[].george";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_modifierUpdateOperation = "$push";
    mf.m_modifierOperationApplyPolicy = "Insert&Update";
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "bob.fred[].george";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_modifierUpdateOperation = "$push";
    mf.m_modifierOperationApplyPolicy = "Insert&Update";
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "jsonField";
    mf.m_mongoDocPath = "bob.fred[].george";
    mf.m_modifierUpdateOperation = "$push";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_modifierOperationApplyPolicy = "Insert&Update";
    mf.m_JSON = true;
    paths.add( mf );

    RowMetaInterface rm = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rm.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_STRING );
    rm.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "jsonField", ValueMetaInterface.TYPE_STRING );
    rm.addValueMeta( vm );

    VariableSpace vars = new Variables();
    Object[] dummyRow = new Object[] { "value1", "value2", "{\"jsonDocField1\" : \"aval\", \"jsonDocField2\" : 42}" };

    DBObject
        modifierUpdate =
        data.getModifierUpdateObject( paths, rm, dummyRow, vars, MongoDbOutputData.MongoTopLevel.RECORD );

    assertTrue( modifierUpdate != null );
    assertTrue( modifierUpdate.get( "$push" ) != null );
    DBObject setOpp = (DBObject) modifierUpdate.get( "$push" );

    // in this case, we have the same path up to the array (bob.fred). The
    // remaining
    // terminal fields should be grouped into one record "george" for $push to
    // append
    // to the end of the array
    assertEquals( setOpp.keySet().size(), 1 );

    assertEquals( modifierUpdate.toString(),
        "{ \"$push\" : { \"bob.fred\" : { \"george\" : { \"field1\" : \"value1\" , \"field2\" : \"value2\" , \"jsonField\" : "
            + "{ \"jsonDocField1\" : \"aval\" , \"jsonDocField2\" : 42}}}}}" );
  }

  @Test public void testInsertKettleFieldThatContainsJsonIntoTopLevelRecord() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "jsonField";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_JSON = true;
    paths.add( mf );

    RowMetaInterface rmi = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_INTEGER );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "jsonField", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );

    Object[] row = new Object[3];
    row[0] = "value1";
    row[1] = new Long( 12 );
    row[2] = "{\"jsonDocField1\" : \"aval\", \"jsonDocField2\" : 42}";
    VariableSpace vs = new Variables();

    for ( MongoDbOutputMeta.MongoField f : paths ) {
      f.init( vs );
    }

    DBObject result = kettleRowToMongo( paths, rmi, row, vs, MongoDbOutputData.MongoTopLevel.RECORD, false );

    assertEquals( result.toString(),
        "{ \"field1\" : \"value1\" , \"field2\" : 12 , \"jsonField\" : { \"jsonDocField1\" : \"aval\" , \"jsonDocField2\" : 42}}" );

  }

  @Test public void testScanForInsertTopLevelJSONDocAsIs() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = false;
    mf.m_JSON = true;
    paths.add( mf );

    assertTrue( MongoDbOutputData.scanForInsertTopLevelJSONDoc( paths ) );
  }

  @Test public void testForInsertTopLevelJSONDocAsIsWithOneJSONMatchPathAndOneJSONInsertPath() throws KettleException {

    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = false;
    mf.m_JSON = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "";
    mf.m_mongoDocPath = "";
    mf.m_updateMatchField = true;
    mf.m_useIncomingFieldNameAsMongoFieldName = false;
    mf.m_JSON = true;
    paths.add( mf );

    assertTrue( MongoDbOutputData.scanForInsertTopLevelJSONDoc( paths ) );
  }

  @Test public void testScanForInsertTopLevelJSONDocAsIsWithMoreThanOnePathSpecifyingATopLevelJSONDocToInsert() {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = false;
    mf.m_JSON = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = false;
    mf.m_JSON = true;
    paths.add( mf );

    try {
      MongoDbOutputData.scanForInsertTopLevelJSONDoc( paths );
      fail( "Was expecting an exception because more than one path specifying a JSON "
          + "doc to insert as-is is not kosher" );
    } catch ( KettleException ex ) {
      // an Exception is expected
    }
  }

  @Test public void testInsertKettleFieldThatContainsJsonIntoOneLevelNestedDoc() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "nestedDoc";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "jsonField";
    mf.m_mongoDocPath = "nestedDoc";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_JSON = true;
    paths.add( mf );

    RowMetaInterface rmi = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_INTEGER );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "jsonField", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );

    Object[] row = new Object[3];
    row[0] = "value1";
    row[1] = new Long( 12 );
    row[2] = "{\"jsonDocField1\" : \"aval\", \"jsonDocField2\" : 42}";
    VariableSpace vs = new Variables();

    for ( MongoDbOutputMeta.MongoField f : paths ) {
      f.init( vs );
    }

    DBObject result = kettleRowToMongo( paths, rmi, row, vs, MongoDbOutputData.MongoTopLevel.RECORD, false );

    assertEquals( result.toString(),
        "{ \"field1\" : \"value1\" , \"nestedDoc\" : { \"field2\" : 12 , \"jsonField\" : { \"jsonDocField1\" : \"aval\" , \"jsonDocField2\" : 42}}}" );
  }

  @Test public void testInsertKettleFieldThatContainsJsonIntoTopLevelArray() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "[0]";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "[1]";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "jsonField";
    mf.m_mongoDocPath = "[2]";
    mf.m_useIncomingFieldNameAsMongoFieldName = false;
    mf.m_JSON = true;
    paths.add( mf );

    RowMetaInterface rmi = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_INTEGER );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "jsonField", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );

    Object[] row = new Object[3];
    row[0] = "value1";
    row[1] = new Long( 12 );
    row[2] = "{\"jsonDocField1\" : \"aval\", \"jsonDocField2\" : 42}";
    VariableSpace vs = new Variables();

    for ( MongoDbOutputMeta.MongoField f : paths ) {
      f.init( vs );
    }

    DBObject result = kettleRowToMongo( paths, rmi, row, vs, MongoDbOutputData.MongoTopLevel.ARRAY, false );

    assertEquals( result.toString(),
        "[ { \"field1\" : \"value1\"} , { \"field2\" : 12} , { \"jsonDocField1\" : \"aval\" , \"jsonDocField2\" : 42}]" );

  }

  @Test public void testGetQueryObjectThatContainsJsonNestedDoc() throws KettleException {
    List<MongoDbOutputMeta.MongoField> paths = new ArrayList<MongoDbOutputMeta.MongoField>();

    MongoDbOutputMeta.MongoField mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field1";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_updateMatchField = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "field2";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_updateMatchField = true;
    paths.add( mf );

    mf = new MongoDbOutputMeta.MongoField();
    mf.m_incomingFieldName = "jsonField";
    mf.m_mongoDocPath = "";
    mf.m_useIncomingFieldNameAsMongoFieldName = true;
    mf.m_updateMatchField = true;
    mf.m_JSON = true;
    paths.add( mf );

    RowMetaInterface rmi = new RowMeta();
    ValueMetaInterface vm = ValueMetaFactory.createValueMeta( "field1", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "field2", ValueMetaInterface.TYPE_INTEGER );
    rmi.addValueMeta( vm );
    vm = ValueMetaFactory.createValueMeta( "jsonField", ValueMetaInterface.TYPE_STRING );
    rmi.addValueMeta( vm );

    Object[] row = new Object[3];
    row[0] = "value1";
    row[1] = new Long( 12 );
    row[2] = "{\"jsonDocField1\" : \"aval\", \"jsonDocField2\" : 42}";
    VariableSpace vs = new Variables();

    for ( MongoDbOutputMeta.MongoField f : paths ) {
      f.init( vs );
    }

    DBObject query = MongoDbOutputData.getQueryObject( paths, rmi, row, vs, MongoDbOutputData.MongoTopLevel.RECORD );

    assertEquals( query.toString(),
        "{ \"field1\" : \"value1\" , \"field2\" : 12 , \"jsonField\" : { \"jsonDocField1\" : \"aval\" , \"jsonDocField2\" : 42}}" );
  }

  /**
   * PDI-9989. If step configuration contains mongo field names which doesn't have equivalents in step input row meta -
   * throw exception, with detailed information.
   * <p/>
   * (expected mongo field name b1 has no equivalent in input row meta)
   *
   * @throws KettleException
   */
  @Test( expected = KettleException.class ) public void testStepIsFailedIfOneOfMongoFieldsNotFound()
    throws KettleException {
    MongoDbOutput output = prepareMongoDbOutputMock();

    final String[] metaNames = new String[] { "a1", "a2", "a3" };
    String[] mongoNames = new String[] { "b1", "a2", "a3" };
    List<MongoDbOutputMeta.MongoField> mongoFields = getMongoFields( mongoNames );
    RowMetaInterface rmi = getStubRowMetaInterface( metaNames );
    EasyMock.replay( output );
    try {
      output.checkInputFieldsMatch( rmi, mongoFields );
    } catch ( KettleException e ) {
      String detailedMessage = e.getMessage();
      Assert.assertTrue( "Exception message mentions fields not found:", detailedMessage.contains( "b1" ) );
      throw new KettleException( e );
    }
  }

  @Test public void dbNameRequiredOnInit() {
    when( stepMetaInterface.getDbName() ).thenReturn( "" );
    assertFalse( "init should return false on fail", dbOutput.init( stepMetaInterface, stepDataInterace ) );
    verify( mockLog ).logError( stringCaptor.capture(), throwableCaptor.capture() );
    assertThat( stringCaptor.getValue(),
        equalTo( BaseMessages.getString( PKG, "MongoDbOutput.Messages.Error.ProblemConnecting", "host", "1010" ) ) );
  }

  @Test public void collectionRequiredOnInit() {
    when( stepMetaInterface.getDbName() ).thenReturn( "dbname" );
    when( stepMetaInterface.getCollection() ).thenReturn( "" );
    assertFalse( "init should return false on fail", dbOutput.init( stepMetaInterface, stepDataInterace ) );
    verify( mockLog ).logError( stringCaptor.capture(), throwableCaptor.capture() );
    assertThat( stringCaptor.getValue(),
        equalTo( BaseMessages.getString( PKG, "MongoDbOutput.Messages.Error.ProblemConnecting", "host", "1010" ) ) );
  }

  @Test public void testInit() {
    setupReturns();
    assertTrue( dbOutput.init( stepMetaInterface, stepDataInterace ) );
    verify( stepDataInterace ).setConnection( mongoClientWrapper );
    verify( stepDataInterace ).getConnection();
  }

  @Test public void testSettingWriteRetriesWriteDelay() {
    setupReturns();
    when( stepMetaInterface.getWriteRetries() ).thenReturn( "111" );
    when( stepMetaInterface.getWriteRetryDelay() ).thenReturn( "222" );
    dbOutput.init( stepMetaInterface, stepDataInterace );
    assertThat( dbOutput.m_writeRetries, equalTo( 111 ) );
    assertThat( dbOutput.m_writeRetryDelay, equalTo( 222 ) );

    // bad data reverts to default
    when( stepMetaInterface.getWriteRetries() ).thenReturn( "foo" );
    when( stepMetaInterface.getWriteRetryDelay() ).thenReturn( "bar" );
    dbOutput.init( stepMetaInterface, stepDataInterace );
    assertThat( dbOutput.m_writeRetries, equalTo( MongoDbOutputMeta.RETRIES ) );
    assertThat( dbOutput.m_writeRetryDelay, equalTo( MongoDbOutputMeta.RETRY_DELAY ) );
  }

  private void setupReturns() {
    when( stepMetaInterface.getDbName() ).thenReturn( "dbname" );
    when( stepMetaInterface.getCollection() ).thenReturn( "collection" );
    when( stepMetaInterface.getAuthenticationUser() ).thenReturn( "joe" );
    when( stepDataInterace.getCollection() ).thenReturn( mongoCollectionWrapper );
    when( trans.isRunning() ).thenReturn( true );
    mongoFields = Arrays.asList( mongoField( "foo" ), mongoField( "bar" ), mongoField( "baz" ) );
    when( stepMetaInterface.getMongoFields() ).thenReturn( mongoFields );
    when( stepDataInterace.getMongoFields() ).thenReturn( mongoFields );
  }

  private MongoDbOutputMeta.MongoField mongoField( String fieldName ) {
    MongoDbOutputMeta.MongoField field = new MongoDbOutputMeta.MongoField();
    field.m_incomingFieldName = fieldName;
    field.m_mongoDocPath = fieldName;
    VariableSpace vars = mock( VariableSpace.class );
    when( vars.environmentSubstitute( anyString() ) ).thenReturn( fieldName );
    field.init( vars );
    return field;
  }

  @Test public void testProcessRowNoInput() throws KettleException, MongoDbException {
    setupReturns();
    dbOutput.init( stepMetaInterface, stepDataInterace );
    // no input has been defined, so should gracefully finish and clean up.
    assertFalse( dbOutput.processRow( stepMetaInterface, stepDataInterace ) );
    verify( mongoClientWrapper ).dispose();
  }

  @Test public void testProcessRowWithInput() throws KettleException, MongoDbException {
    setupReturns();
    setupRowMeta();
    dbOutput.init( stepMetaInterface, stepDataInterace );
    assertTrue( dbOutput.processRow( stepMetaInterface, stepDataInterace ) );
  }

  private void setupRowMeta() {
    rowData = new Object[] { "foo", "bar", "baz" };
    rowMeta.addValueMeta( new ValueMetaString( "foo" ) );
    rowMeta.addValueMeta( new ValueMetaString( "bar" ) );
    rowMeta.addValueMeta( new ValueMetaString( "baz" ) );
  }

  @Test public void testUpdate() throws KettleException, MongoDbException {
    setupReturns();
    WriteResult result = mock( WriteResult.class );
    CommandResult commandResult = mock( CommandResult.class );
    when( commandResult.ok() ).thenReturn( true );
    when( result.getLastError() ).thenReturn( commandResult );
    when( mongoCollectionWrapper.update( any( DBObject.class ), any( DBObject.class ), anyBoolean(), anyBoolean() ) )
        .thenReturn( result );
    when( stepMetaInterface.getUpdate() ).thenReturn( true );

    // flag a field for update = "foo"
    MongoDbOutputMeta.MongoField mongoField = mongoFields.get( 0 );
    mongoField.m_updateMatchField = true;

    setupRowMeta();
    dbOutput.init( stepMetaInterface, stepDataInterace );
    assertTrue( dbOutput.processRow( stepMetaInterface, stepDataInterace ) );
    ArgumentCaptor<BasicDBObject> updateQueryCaptor = ArgumentCaptor.forClass( BasicDBObject.class );
    ArgumentCaptor<BasicDBObject> insertCaptor = ArgumentCaptor.forClass( BasicDBObject.class );

    // update is executed
    verify( mongoCollectionWrapper )
        .update( updateQueryCaptor.capture(), insertCaptor.capture(), anyBoolean(), anyBoolean() );
    // result is checked
    verify( result ).getLastError();
    // updated field is expected
    assertThat( updateQueryCaptor.getValue(), equalTo( new BasicDBObject( "foo", "foo" ) ) );
    // insert document is expected
    assertThat( insertCaptor.getValue(),
        equalTo( new BasicDBObject( ( ImmutableMap.of( "foo", "foo", "bar", "bar", "baz", "baz" ) ) ) ) );
  }

  @Test public void updateFailureRetries() throws MongoDbException, KettleException {
    setupReturns();
    setupRowMeta();
    // retry twice with no delay
    when( stepMetaInterface.getWriteRetries() ).thenReturn( "2" );
    when( stepMetaInterface.getWriteRetryDelay() ).thenReturn( "0" );
    when( stepMetaInterface.getUpdate() ).thenReturn( true );
    MongoDbOutputMeta.MongoField mongoField = mongoFields.get( 0 );
    mongoField.m_updateMatchField = true;

    when( mongoCollectionWrapper.update( any( DBObject.class ), any( DBObject.class ), anyBoolean(), anyBoolean() ) )
        .thenThrow( mock( MongoDbException.class ) );

    dbOutput.init( stepMetaInterface, stepDataInterace );
    try {
      dbOutput.processRow( stepMetaInterface, stepDataInterace );
      fail( "expected exception" );
    } catch ( KettleException ke ) {
      // update should be called 3 times (first time plus 2 retries)
      verify( mongoCollectionWrapper, times( 3 ) )
          .update( any( DBObject.class ), any( DBObject.class ), anyBoolean(), anyBoolean() );
    }
  }

  @Test public void doBatchWithRetry() throws KettleException, MongoDbException {
    setupReturns();
    setupRowMeta();
    dbOutput.m_batch = new ArrayList<DBObject>();
    dbOutput.m_batch.add( new BasicDBObject( ImmutableMap.of( "foo", "fooval", "bar", "barval", "baz", "bazval" ) ) );
    List<Object[]> batchRows = new ArrayList<Object[]>();
    batchRows.add( rowData );

    List<DBObject> batchCopy = new ArrayList( dbOutput.m_batch );

    dbOutput.m_batchRows = batchRows;
    when( stepMetaInterface.getWriteRetries() ).thenReturn( "1" );
    when( stepMetaInterface.getWriteRetryDelay() ).thenReturn( "0" );
    WriteResult result = mock( WriteResult.class );
    CommandResult commandResult = mock( CommandResult.class );
    when( commandResult.ok() ).thenReturn( true );
    when( result.getLastError() ).thenReturn( commandResult );
    when( mongoCollectionWrapper.save( dbOutput.m_batch.get( 0 ) ) ).thenReturn( result );

    doThrow( mock( MongoException.class ) ).when( mongoCollectionWrapper ).insert( anyList() );
    dbOutput.init( stepMetaInterface, stepDataInterace );
    dbOutput.doBatch();

    // should attempt insert once, falling back to save on retry
    verify( mongoCollectionWrapper, times( 1 ) ).insert( anyList() );
    verify( mongoCollectionWrapper, times( 1 ) ).save( batchCopy.get( 0 ) );

    // batch should be cleared.
    assertThat( dbOutput.m_batch.size(), equalTo( 0 ) );
    assertThat( dbOutput.m_batchRows.size(), equalTo( 0 ) );
  }

  @Test public void testDisposeFailureLogged() throws MongoDbException {
    doThrow( new MongoDbException() ).when( mongoClientWrapper ).dispose();
    dbOutput.init( stepMetaInterface, stepDataInterace );
    dbOutput.dispose( stepMetaInterface, stepDataInterace );
    verify( mockLog ).logError( anyString() );
  }

  /**
   * Tests if mongo output configuration contains excessive fields in step input against mongo output fields, we
   * generate a mockLog record about the fields will not be used in mongo output.
   *
   * @throws KettleException
   */
  @Test public void testStepLogSkippedFields() throws KettleException {
    MongoDbOutput output = prepareMongoDbOutputMock();

    final String[] metaNames = new String[] { "a1", "a2", "a3" };
    String[] mongoNames = new String[] { "a1", "a2" };
    Capture<String> loggerCapture = new Capture<String>( CaptureType.ALL );
    output.logBasic( EasyMock.capture( loggerCapture ) );
    EasyMock.replay( output );
    RowMetaInterface rmi = getStubRowMetaInterface( metaNames );
    List<MongoDbOutputMeta.MongoField> mongoFields = getMongoFields( mongoNames );

    output.checkInputFieldsMatch( rmi, mongoFields );
    List<String> logRecords = loggerCapture.getValues();

    Assert.assertEquals( "We have one mockLog record generated", 1, logRecords.size() );
    Assert.assertTrue( "We have a mockLog record mentions that 'a3' field will not be used.",
        logRecords.get( 0 ).contains( "a3" ) );
  }

  /**
   * Tests if none mongo output fields exists - step is failed.
   *
   * @throws KettleException
   */
  @Test( expected = KettleException.class ) public void testStepFailsIfNoMongoFieldsFound() throws KettleException {
    MongoDbOutput output = prepareMongoDbOutputMock();

    final String[] metaNames = new String[] { "a1", "a2" };
    String[] mongoNames = new String[] {};
    EasyMock.replay( output );
    RowMetaInterface rmi = getStubRowMetaInterface( metaNames );
    List<MongoDbOutputMeta.MongoField> mongoFields = getMongoFields( mongoNames );
    output.checkInputFieldsMatch( rmi, mongoFields );
  }

  @Test public void testStepMetaSetsUpdateInXML()
    throws ParserConfigurationException, UnsupportedEncodingException, SAXException, IOException, KettleXMLException {
    MongoDbOutputMeta mongoMeta = new MongoDbOutputMeta();
    mongoMeta.setUpdate( true );

    String XML = mongoMeta.getXML();
    XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<step>\n" + XML + "\n</step>\n";

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();
    Document doc = db.parse( new ByteArrayInputStream( XML.getBytes( "UTF-8" ) ) );

    NodeList step = doc.getElementsByTagName( "step" );
    Node stepNode = step.item( 0 );

    MongoDbOutputMeta newMeta = new MongoDbOutputMeta();
    newMeta.loadXML( stepNode, (List<DatabaseMeta>) null, (IMetaStore) null );

    assertTrue( newMeta.getUpdate() );
    assertFalse( newMeta.getUpsert() );
  }

  @Test public void testStepMetaSettingUpsertAlsoSetsUpdateInXML()
    throws ParserConfigurationException, UnsupportedEncodingException, SAXException, IOException, KettleXMLException {
    MongoDbOutputMeta mongoMeta = new MongoDbOutputMeta();
    mongoMeta.setUpsert( true );

    String XML = mongoMeta.getXML();
    XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<step>\n" + XML + "\n</step>\n";

    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();
    Document doc = db.parse( new ByteArrayInputStream( XML.getBytes( "UTF-8" ) ) );

    NodeList step = doc.getElementsByTagName( "step" );
    Node stepNode = step.item( 0 );

    MongoDbOutputMeta newMeta = new MongoDbOutputMeta();
    newMeta.loadXML( stepNode, (List<DatabaseMeta>) null, (IMetaStore) null );

    assertTrue( newMeta.getUpsert() );
    assertTrue( newMeta.getUpdate() );
  }

  private MongoDbOutput prepareMongoDbOutputMock() {
    MongoDbOutput output = EasyMock.createNiceMock( MongoDbOutput.class );
    EasyMock.expect( output.environmentSubstitute( EasyMock.anyObject( String.class ) ) )
        .andAnswer( new IAnswer<String>() {
          @Override public String answer() throws Throwable {
            Object[] args = EasyMock.getCurrentArguments();
            String ret = String.valueOf( args[0] );
            return ret;
          }
        } ).anyTimes();
    return output;
  }

  private RowMetaInterface getStubRowMetaInterface( final String[] metaNames ) {
    RowMetaInterface rmi = EasyMock.createNiceMock( RowMetaInterface.class );
    EasyMock.expect( rmi.getFieldNames() ).andReturn( metaNames ).anyTimes();
    EasyMock.expect( rmi.size() ).andReturn( metaNames.length ).anyTimes();
    EasyMock.expect( rmi.getValueMeta( EasyMock.anyInt() ) ).andAnswer( new IAnswer<ValueMetaInterface>() {
      @Override public ValueMetaInterface answer() throws Throwable {
        Object[] args = EasyMock.getCurrentArguments();
        int i = Integer.class.cast( args[0] );
        ValueMetaInterface ret = new ValueMetaString( metaNames[i] );
        return ret;
      }
    } ).anyTimes();
    EasyMock.replay( rmi );
    return rmi;
  }

  private List<MongoDbOutputMeta.MongoField> getMongoFields( String[] names ) {
    List<MongoDbOutputMeta.MongoField> ret = new ArrayList<MongoDbOutputMeta.MongoField>( names.length );
    for ( String name : names ) {
      MongoDbOutputMeta.MongoField field = new MongoDbOutputMeta.MongoField();
      field.m_incomingFieldName = name;
      ret.add( field );
    }
    return ret;
  }

}
