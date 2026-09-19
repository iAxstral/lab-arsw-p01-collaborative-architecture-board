package edu.eci.arsw.collabboard.domain.model;
import org.junit.jupiter.api.Test; import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class BoardConnectorTest {
 @Test void validConnectorReferencesExistingElements(){
   var a=new BoardElement("a",ElementType.RECTANGLE,0,0,100,60,"A",null,null); var b=new BoardElement("b",ElementType.TEXT,200,0,100,30,"B",null,null); var c=new BoardElement("c",ElementType.CONNECTOR,0,0,0,0,"","a","b");
   assertDoesNotThrow(()->new Board("board","Demo",List.of(a,b,c)));
 }
 @Test void connectorCannotReferenceAnotherConnector(){
   var a=new BoardElement("a",ElementType.RECTANGLE,0,0,100,60,"A",null,null); var b=new BoardElement("b",ElementType.RECTANGLE,200,0,100,60,"B",null,null);
   var c1=new BoardElement("c1",ElementType.CONNECTOR,0,0,0,0,"","a","b"); var c2=new BoardElement("c2",ElementType.CONNECTOR,0,0,0,0,"","a","c1");
   assertThrows(IllegalArgumentException.class,()->new Board("board","Demo",List.of(a,b,c1,c2)));
 }
 @Test void connectorCannotReferenceMissingElement(){
   var a=new BoardElement("a",ElementType.RECTANGLE,0,0,100,60,"A",null,null); var c=new BoardElement("c",ElementType.CONNECTOR,0,0,0,0,"","a","missing");
   assertThrows(IllegalArgumentException.class,()->new Board("board","Demo",List.of(a,c)));
 }
}
