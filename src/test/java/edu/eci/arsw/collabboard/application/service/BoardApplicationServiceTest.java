package edu.eci.arsw.collabboard.application.service;
import edu.eci.arsw.collabboard.application.exception.BoardNotFoundException;
import edu.eci.arsw.collabboard.domain.model.*;
import edu.eci.arsw.collabboard.infrastructure.persistence.InMemoryBoardRepository;
import org.junit.jupiter.api.Test; import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class BoardApplicationServiceTest {
 private final BoardApplicationService s=new BoardApplicationService(new InMemoryBoardRepository());
 @Test void createReadReplace(){ Board b=s.createBoard("A"); assertEquals(b,s.getBoard(b.id())); Board r=s.replaceBoard(b.id(),"B",List.of(new BoardElement("e",ElementType.RECTANGLE,10,10,100,50,"API",null,null))); assertEquals("B",r.name()); }
 @Test void missing(){ assertThrows(BoardNotFoundException.class,()->s.getBoard("missing")); }
}
