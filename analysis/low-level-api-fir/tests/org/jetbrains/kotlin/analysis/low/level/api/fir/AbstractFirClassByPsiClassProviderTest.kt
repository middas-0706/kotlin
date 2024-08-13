/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.low.level.api.fir

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.childrenOfType
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.low.level.api.fir.providers.firClassByPsiClassProvider
import org.jetbrains.kotlin.analysis.low.level.api.fir.test.configurators.AnalysisApiFirSourceTestConfigurator
import org.jetbrains.kotlin.analysis.test.framework.base.AbstractAnalysisApiBasedTest
import org.jetbrains.kotlin.analysis.test.framework.projectStructure.ktTestModuleStructure
import org.jetbrains.kotlin.analysis.test.framework.services.expressionMarkerProvider
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.assertions
import org.jetbrains.kotlin.test.services.moduleStructure
import kotlin.collections.any
import kotlin.collections.flatMap
import kotlin.collections.single
import kotlin.collections.toList
import kotlin.text.endsWith

abstract class AbstractFirClassByPsiClassProviderTest : AbstractAnalysisApiBasedTest() {
    override val configurator = AnalysisApiFirSourceTestConfigurator(analyseInDependentSession = false)

    override fun doTest(testServices: TestServices) {
        val useSiteModule = testServices.moduleStructure.modules.single()
        val useSiteKtModule = testServices.ktTestModuleStructure.getKtTestModule(useSiteModule).ktModule
        val psiClassUnderCaret = when (useSiteKtModule) {
            is KaSourceModule -> {
                testServices.expressionMarkerProvider.getElementsOfTypeAtCarets<PsiClass>(testServices).single().first
            }
            is KaLibraryModule -> {
                testServices.ktTestModuleStructure.getKtTestModule(useSiteModule).files
                    .filterIsInstance<PsiJavaFile>()
                    .flatMap { it.classes.toList() }
                    .flatMap { it.withAllNestedClasses() }
                    .single { it.annotations.any { it.qualifiedName?.endsWith("Caret") == true } }
            }
            else -> {
                error("Unexpected module kind ${useSiteKtModule::class.simpleName}")
            }
        }
        val resolveSession = LLFirResolveSessionService.getInstance(useSiteKtModule.project).getFirResolveSessionNoCaching(useSiteKtModule)
        val firClass = resolveSession.useSiteFirSession.firClassByPsiClassProvider.getFirClass(psiClassUnderCaret)!!
        val rendered = firClass.fir.render()
        testServices.assertions.assertEqualsToTestDataFileSibling(rendered)
    }

    private fun PsiClass.withAllNestedClasses(): List<PsiClass> = buildList {
        fun collect(psiClass: PsiClass) {
            add(psiClass)
            psiClass.innerClasses.forEach(::collect)
        }
        collect(this@withAllNestedClasses)
    }
}