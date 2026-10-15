package com.forward.hrms.chat.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "message_mentions")
@Getter
@Setter
public class MessageMention {

    @EmbeddedId
    private MessageMentionId id;
}
