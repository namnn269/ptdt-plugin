package com.fds.flexdata.plugin.ptdt.configuration;

import org.pf4j.PluginWrapper;
import org.pf4j.spring.SpringPlugin;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class PhatTrienDoThiPlugin extends SpringPlugin {

    private final PluginWrapper pluginWrapper;
    private ApplicationContext parentContext;

    public PhatTrienDoThiPlugin(PluginWrapper wrapper) {
        super(wrapper);
        this.pluginWrapper = wrapper;
        if (wrapper.getPluginManager() instanceof SpringPluginManager springPluginManager) {
            this.parentContext = springPluginManager.getApplicationContext();
        }
    }

    @Override
    protected ApplicationContext createApplicationContext() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setParent(parentContext);
        context.setClassLoader(pluginWrapper.getPluginClassLoader());
        context.register(PluginConfig.class);
        context.refresh();
        return context;
    }
}
