package com.github.jikoo.regionerator.util;

import java.util.function.Consumer;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * An EventExecutor based on a provided Consumer.
 *
 * @param <T> the event type
 * @author Jikoo
 */
public class ConsumerEventExecutor<T extends Event> implements EventExecutor {

	private final Class<T> eventClass;
	private final Consumer<T> eventConsumer;

	public ConsumerEventExecutor(Class<T> eventClass, Consumer<T> eventConsumer) {
		this.eventClass = eventClass;
		this.eventConsumer = eventConsumer;
	}

	@Override
	public void execute(@NotNull Listener listener, @NotNull Event event) throws EventException {
		if (eventClass.isInstance(event)) {
			eventConsumer.accept(eventClass.cast(event));
		}
	}

}
