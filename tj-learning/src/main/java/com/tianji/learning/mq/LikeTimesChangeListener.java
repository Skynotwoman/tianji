package com.tianji.learning.mq;

import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.QA_LIKED_TIMES_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
public class LikeTimesChangeListener {
    private final IInteractionReplyService replyService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "qa.like.time.change", durable = "ture"),
            exchange = @Exchange(name = LIKE_RECORD_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = QA_LIKED_TIMES_KEY
    ))
    public void listenReplyTimesChange(LikeTimesDTO likeTimesDTO){
        log.debug("监听到回答或点评的点赞数变更消息：{}，点赞数：{}",
                likeTimesDTO.getBizId(), likeTimesDTO.getLikeTimes());
        InteractionReply r = new InteractionReply();
        r.setId(likeTimesDTO.getBizId());
        r.setLikedTimes(likeTimesDTO.getLikeTimes());
        replyService.updateById(r);
    }
}
