package com.github.jikoo.regionerator.util;

import java.util.function.Consumer;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.jetbrains.annotations.NotNull;

/**
 * A simplified listener for handling events via Consumer instead of Method.
 *
 * @param <T> the event type
 * @author Jikoo
 */
public class SimpleListener<T extends Event> extends RegisteredListener {

	public SimpleListener(@NotNull Class<T> eventClass, @NotNull Consumer<T> consumer, @NotNull Plugin plugin) {
		this(eventClass, consumer, plugin, EventPriority.NORMAL, true);
	}

	public SimpleListener(@NotNull Class<T> eventClass, @NotNull Consumer<T> consumer, @NotNull Plugin plugin,
			@NotNull EventPriority priority) {
		this(eventClass, consumer, plugin, priority, true);
	}

	public SimpleListener(@NotNull Class<T> eventClass, @NotNull Consumer<T> consumer, @NotNull Plugin plugin,
			@NotNull EventPriority priority, boolean ignoreCancelled) {
		super(new Listener() {}, new ConsumerEventExecutor<>(eventClass, consumer), priority, plugin, ignoreCancelled);
	}

}
