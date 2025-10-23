package org.tablebuilder.demo.store;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByChatIdOrderByMessageOrderAsc(String chatId);

    @Modifying
    @Query("DELETE FROM ChatMessage cm WHERE cm.chatId = :chatId")
    void deleteByChatId(@Param("chatId") String chatId);

    @Query("SELECT COUNT(cm) FROM ChatMessage cm WHERE cm.chatId = :chatId")
    long countByChatId(@Param("chatId") String chatId);
}