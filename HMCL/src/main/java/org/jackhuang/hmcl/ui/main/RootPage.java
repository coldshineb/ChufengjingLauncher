/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.main;

import javafx.beans.property.ReadOnlyObjectProperty;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.account.AccountAdvancedListItem;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.AdvancedListItem;
import org.jackhuang.hmcl.ui.construct.JFXHyperlink;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.nbt.NBTEditorPage;
import org.jackhuang.hmcl.ui.nbt.NBTHelper;
import org.jackhuang.hmcl.ui.versions.GameAdvancedListItem;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.upgrade.hmcl.UpdateChecker;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.File;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class RootPage extends DecoratorAnimatedPage implements DecoratorPage {
    private MainPage mainPage = null;

    public RootPage() {
        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class)
                .register(event -> onRefreshedVersions((HMCLGameRepository) event.getSource()));

        Profile profile = Profiles.getSelectedProfile();
        if (profile != null && profile.getRepository().isLoaded())
            onRefreshedVersions(Profiles.selectedProfileProperty().get().getRepository());

        getStyleClass().remove("gray-background");
        getLeft().getStyleClass().add("gray-background");
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return getMainPage().stateProperty();
    }

    @Override
    protected Skin createDefaultSkin() {
        return new Skin(this);
    }

    public MainPage getMainPage() {
        if (mainPage == null) {
            MainPage mainPage = new MainPage();
            FXUtils.applyDragListener(mainPage,
                    file -> ModpackHelper.isFileModpackByExtension(file) || NBTHelper.isNBTFileByExtension(file),
                    modpacks -> {
                        File file = modpacks.get(0);
                        if (ModpackHelper.isFileModpackByExtension(file)) {
                            Controllers.getDecorator().startWizard(
                                    new ModpackInstallWizardProvider(Profiles.getSelectedProfile(), file),
                                    i18n("install.modpack"));
                        } else if (NBTHelper.isNBTFileByExtension(file)) {
                            try {
                                Controllers.navigate(new NBTEditorPage(file));
                            } catch (Throwable e) {
                                LOG.log(Level.WARNING, "Fail to open nbt file", e);
                                Controllers.dialog(i18n("nbt.open.failed") + "\n\n" + StringUtils.getStackTrace(e),
                                        i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                            }
                        }
                    });

            FXUtils.onChangeAndOperate(Profiles.selectedVersionProperty(), mainPage::setCurrentGame);
            mainPage.showUpdateProperty().bind(UpdateChecker.outdatedProperty());
            mainPage.latestVersionProperty().bind(UpdateChecker.latestVersionProperty());

            Profiles.registerVersionsListener(profile -> {
                HMCLGameRepository repository = profile.getRepository();
                List<Version> children = repository.getVersions().parallelStream()
                        .filter(version -> !version.isHidden())
                        .sorted(Comparator
                                .comparing((Version version) -> version.getReleaseTime() == null ? new Date(0L)
                                        : version.getReleaseTime())
                                .thenComparing(a -> VersionNumber.asVersion(a.getId())))
                        .collect(Collectors.toList());
                runInFX(() -> {
                    if (profile == Profiles.getSelectedProfile())
                        mainPage.initVersions(profile, children);
                });
            });
            this.mainPage = mainPage;
        }
        return mainPage;
    }

    private static class Skin extends DecoratorAnimatedPageSkin<RootPage> {

        protected Skin(RootPage control) {
            super(control);

            // first item in left sidebar
            AccountAdvancedListItem accountListItem = new AccountAdvancedListItem();
            accountListItem.setOnAction(e -> Controllers.navigate(Controllers.getAccountListPage()));
            accountListItem.accountProperty().bind(Accounts.selectedAccountProperty());

            // second item in left sidebar
            GameAdvancedListItem gameListItem = new GameAdvancedListItem();
            gameListItem.setOnAction(e -> {
                Profile profile = Profiles.getSelectedProfile();
                String version = Profiles.getSelectedVersion();
                if (version == null) {
                    Controllers.navigate(Controllers.getGameListPage());
                } else {
                    Versions.modifyGameSettings(profile, version);
                }
            });

            // sixth item in left sidebar
            AdvancedListItem launcherSettingsItem = new AdvancedListItem();
            launcherSettingsItem.setLeftGraphic(wrap(SVG.GEAR_OUTLINE));
            launcherSettingsItem.setActionButtonVisible(false);
            launcherSettingsItem.setTitle(i18n("settings"));
            launcherSettingsItem.setOnAction(e -> Controllers.navigate(Controllers.getSettingsPage()));

            // help-navigation
            AdvancedListItem helpNavigationItem = new AdvancedListItem();
            helpNavigationItem.setLeftGraphic(wrap(SVG.HELP_CIRCLE_OUTLINE));
            helpNavigationItem.setActionButtonVisible(false);
            helpNavigationItem.setTitle(i18n("gennokioku.help_navigation"));
            helpNavigationItem.setOnAction(e -> FXUtils.openLink("https://wiki.gennokioku.city/help-navigation"));

            // terms
            AdvancedListItem termsItem = new AdvancedListItem();
            termsItem.setLeftGraphic(wrap(SVG.BOOK_OUTLINE));
            termsItem.setActionButtonVisible(false);
            termsItem.setTitle(i18n("gennokioku.terms"));
            termsItem.setOnAction(e -> FXUtils.openLink("https://wiki.gennokioku.city/terms"));

            // changelog
            AdvancedListItem changelogItem = new AdvancedListItem();
            changelogItem.setLeftGraphic(wrap(SVG.STAR_OUTLINE));
            changelogItem.setActionButtonVisible(false);
            changelogItem.setTitle(i18n("gennokioku.changelog"));
            changelogItem.setOnAction(e -> FXUtils.openLink("https://wiki.gennokioku.city/changelog"));

            // the left sidebar
            AdvancedListBox sideBar = new AdvancedListBox()
                    .startCategory(i18n("account").toUpperCase(Locale.ROOT))
                    .add(accountListItem)
                    .startCategory(i18n("version").toUpperCase(Locale.ROOT))
                    .add(gameListItem)
                    .startCategory(i18n("settings.launcher.general").toUpperCase(Locale.ROOT))
                    .add(launcherSettingsItem)
                    .startCategory("Gennokioku")
                    .add(helpNavigationItem)
                    .add(termsItem)
                    .add(changelogItem);

            // the root page, with the sidebar in left, navigator in center.
            setLeft(sideBar);
            setCenter(getSkinnable().getMainPage());
        }

    }

    private boolean checkedModpack = false;

    private void onRefreshedVersions(HMCLGameRepository repository) {
        runInFX(() -> {
            if (!checkedModpack) {
                checkedModpack = true;

                if (repository.getVersionCount() == 0) {
                    File modpackFile = new File("modpack.zip").getAbsoluteFile();
                    if (modpackFile.exists()) {
                        Task.supplyAsync(() -> CompressingUtils.findSuitableEncoding(modpackFile.toPath()))
                                .thenApplyAsync(
                                        encoding -> ModpackHelper.readModpackManifest(modpackFile.toPath(), encoding))
                                .thenApplyAsync(modpack -> ModpackHelper
                                        .getInstallTask(repository.getProfile(), modpackFile, modpack.getName(),
                                                modpack)
                                        .executor())
                                .thenAcceptAsync(Schedulers.javafx(), executor -> {
                                    Controllers.taskDialog(executor, i18n("modpack.installing"), TaskCancellationAction.NO_CANCEL);
                                    executor.start();
                                }).start();
                    }
                }
            }
        });
    }
}
